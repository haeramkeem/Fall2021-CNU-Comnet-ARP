import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.packet.PcapPacketHandler;

public class NILayer implements BaseLayer {

	// ----- Properties -----
	private int nUpperLayerCount = 0;
	private String pLayerName = null;
	private BaseLayer p_UnderLayer = null;
	private ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
	private int m_iNumAdapter;
	private Pcap m_AdapterObject;
	private ArrayList<PcapIf> m_pAdapterList;
	private StringBuilder errbuf = new StringBuilder();
	private Logger logging;

	// ----- Static -----
	static {
		try {
			String jnetpcapPath = new File("jnetpcap.dll").getAbsolutePath();
			System.load(jnetpcapPath);
			
			System.out.println("[NI]JNetPcap loded: " + jnetpcapPath);
		} catch (UnsatisfiedLinkError e) {
			System.err.println("[NI]JNetPcap failed to load: " + e);
			System.exit(0);
		}

	}

	// ----- Constructor -----
	public NILayer(String pName) {
		pLayerName = pName;
		m_pAdapterList = new ArrayList<PcapIf>();
		m_iNumAdapter = 0;
		logging = new Logger(this);
		setAdapterList();
	}

	public void setAdapterList() {
		int r = Pcap.findAllDevs(m_pAdapterList, errbuf);
		logging.log("Adapters found: "+m_pAdapterList.size());
		if (r != Pcap.OK || m_pAdapterList.isEmpty())
			logging.panic("No adapters found: " + errbuf.toString(), null);
	}

	// ----- Methods -----
	public void packetStartDriver() {
		int snaplen = 64 * 1024; // Capture all packets, no trucation
		int flags = Pcap.MODE_PROMISCUOUS; // capture all packets
		int timeout = 10 * 1000; // 10 seconds in millis
		m_AdapterObject = Pcap.openLive(m_pAdapterList.get(m_iNumAdapter).getName(), snaplen, flags, timeout, errbuf);
	}

	public void setAdapterNumber(int iNum) {
		m_iNumAdapter = iNum;
		packetStartDriver();
		receive();
	}

	public boolean send(byte[] input, int length) {
		ByteBuffer buf = ByteBuffer.wrap(input);
		logging.log("Send");
		if (m_AdapterObject.sendPacket(buf) != Pcap.OK) {
			logging.error(m_AdapterObject.getErr());
			return false;
		}
		return true;
	}

	public boolean receive() {
		Receive_Thread thread = new Receive_Thread(m_AdapterObject, this.getUpperLayer(0));
		Thread obj = new Thread(thread);
		obj.start();
		logging.log("Receive thread start");
		return false;
	}

	// ----- Getters & Setters -----
	public PcapIf getAdapterObject(int iIndex) {
		return m_pAdapterList.get(iIndex);
	}

	public ArrayList<PcapIf> getAdapterList() {
		return m_pAdapterList;
	}

	@Override
	public void setUnderLayer(BaseLayer pUnderLayer) {
		if (pUnderLayer == null)
			return;
		p_UnderLayer = pUnderLayer;
	}

	@Override
	public void setUpperLayer(BaseLayer pUpperLayer) {
		if (pUpperLayer == null)
			return;
		this.p_aUpperLayer.add(nUpperLayerCount++, pUpperLayer);
	}

	@Override
	public String getLayerName() {
		return pLayerName;
	}

	@Override
	public BaseLayer getUnderLayer() {
		if (p_UnderLayer == null)
			return null;
		return p_UnderLayer;
	}

	@Override
	public BaseLayer getUpperLayer(int nindex) {
		if (nindex < 0 || nindex > nUpperLayerCount || nUpperLayerCount < 0)
			return null;
		return p_aUpperLayer.get(nindex);
	}

	@Override
	public void setUpperUnderLayer(BaseLayer pUULayer) {
		this.setUpperLayer(pUULayer);
		pUULayer.setUnderLayer(this);

	}
}

// ----- Thread class -----
class Receive_Thread implements Runnable {
	byte[] data;
	Pcap AdapterObject;
	BaseLayer UpperLayer;

	public Receive_Thread(Pcap m_AdapterObject, BaseLayer m_UpperLayer) {
		AdapterObject = m_AdapterObject;
		UpperLayer = m_UpperLayer;
	}

	@Override
	public void run() {
		while (true) {
			PcapPacketHandler<String> jpacketHandler = new PcapPacketHandler<String>() {
				public void nextPacket(PcapPacket packet, String user) {
					System.out.println("[NI]: Receive");
					data = packet.getByteArray(0, packet.size());
					UpperLayer.receive(data);
				}
			};

			AdapterObject.loop(100000, jpacketHandler, "");
		}
	}
}
