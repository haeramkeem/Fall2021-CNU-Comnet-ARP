import java.util.ArrayList;

public class TCPLayer implements BaseLayer {

	private static final int CHAT_PORT = 0;
	private static final int FILE_PORT = 1;
	private static final int HEADER_SIZE = 24;

	// ----- Properties -----
	private int nUpperLayerCount = 0;
	private String pLayerName = null;
	private BaseLayer p_UnderLayer = null;
	private ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
	private _TCP m_sHeader;
	private Logger logging;

	// ----- Constructor -----
	public TCPLayer(String pName) {
		pLayerName = pName;
		m_sHeader = new _TCP();
		logging = new Logger(this);
	}

	// ----- Structures -----
	private class _TCP {
		byte[] tcp_sport; 		// source port (2byte)
		byte[] tcp_dport;		// destination port (2byte)
		byte[] tcp_seq; 		// sequence number (4byte)			!NOT USED!
		byte[] tcp_ack; 		// acknowledged sequence (4byte)	!NOT USED!
		byte tcp_offset;		// no use (1byte)					!NOT USED!
		byte tcp_flag; 			// control flag (1byte)				!NOT USED!
		byte[] tcp_window;		// no use (2 byte)					!NOT USED!
		byte[] tcp_cksum;		// checksum (2 byte)				!NOT USED!
		byte[] tcp_urgptr;		// no use (2 byte)					!NOT USED!
		byte[] padding;			// padding (4 byte)					!NOT USED!
		byte[] tcp_data;		// data part (n byte)

		public _TCP() {
			this.tcp_sport = new byte[2];
			this.tcp_dport = new byte[2];
			this.tcp_seq = new byte[4];			// !NOT USED!
			this.tcp_ack = new byte[4];			// !NOT USED!
			this.tcp_offset = 0x00;				// !NOT USED!
			this.tcp_flag = 0x00;				// !NOT USED!
			this.tcp_window = new byte[2];		// !NOT USED!
			this.tcp_cksum = new byte[2];		// !NOT USED!
			this.tcp_urgptr = new byte[2];		// !NOT USED!
			this.padding = new byte[4];			// !NOT USED!
			this.tcp_data = null;
		}
	}

	// ----- Methods -----
	// Sending
	public boolean sendChat(byte[] input, int length) {
		this.m_sHeader.tcp_sport = intToByte2(CHAT_PORT);
		this.m_sHeader.tcp_dport = intToByte2(CHAT_PORT);
		logging.log("Send chat");
		return this.sendData(input, length);
	}

	public boolean sendFile(byte[] input, int length) {
		this.m_sHeader.tcp_sport = intToByte2(FILE_PORT);
		this.m_sHeader.tcp_dport = intToByte2(FILE_PORT);
		logging.log("Send file");
		return this.sendData(input, length);
	}

	private boolean sendData(byte[] input, int length) {
		byte[] tempByte = objToByte(m_sHeader, input, length);
		logging.log("Send data");
		return ((IPLayer)this.getUnderLayer()).send(tempByte, length + HEADER_SIZE);
	}

	public boolean sendARP(byte[] dstAddr) {
		logging.log("Send ARP");
		return ((IPLayer)this.getUnderLayer()).sendARP(dstAddr);
	}

	public boolean sendGARP(byte[] srcMac) {
		logging.log("Send GARP");
		return ((IPLayer)this.getUnderLayer()).sendGARP(srcMac);
	}

	// Receiving
	private _TCP byteToObj(byte[] input) {
		_TCP temp = new _TCP();

		// Copying Header
		System.arraycopy(input, 0, temp.tcp_sport, 0, 2);
		System.arraycopy(input, 2, temp.tcp_dport, 0, 2);
		System.arraycopy(input, 4, temp.tcp_seq, 0, 4);
		System.arraycopy(input, 8, temp.tcp_ack, 0, 4);
		temp.tcp_offset = input[12];
		temp.tcp_flag = input[13];
		System.arraycopy(input, 14, temp.tcp_window, 0, 2);
		System.arraycopy(input, 16, temp.tcp_cksum, 0, 2);
		System.arraycopy(input, 18, temp.tcp_urgptr, 0, 2);
		System.arraycopy(input, 20, temp.padding, 0, 4);

		// Copying Data
		temp.tcp_data = removeTCPHeader(input, input.length);

		return temp;
	}

	private byte[] removeTCPHeader(byte[] input, int length) {
		byte[] cpyInput = new byte[length - HEADER_SIZE];
		System.arraycopy(input, HEADER_SIZE, cpyInput, 0, length - HEADER_SIZE);
		return cpyInput;
	}

	public synchronized boolean receive(byte[] input) {
		_TCP received = byteToObj(input);
		int sport = byte2ToInt(received.tcp_sport[0], received.tcp_sport[1]);
		int dport = byte2ToInt(received.tcp_dport[0], received.tcp_dport[1]);

		// Assume that src-port and dst-port must be same
		if(sport == dport) {
			if(dport == CHAT_PORT) {
				logging.log("Receive chat");
				return ((ChatAppLayer)this.getUpperLayer(0)).receive(received.tcp_data);
			} else if(dport == FILE_PORT) {
				logging.log("Receive file");
				return ((FileAppLayer)this.getUpperLayer(1)).receive(received.tcp_data);
			}
		}

		return false;
	}

	// ----- Convert methods -----
	private byte[] objToByte(_TCP Header, byte[] input, int length) {
		byte[] buf = new byte[length + HEADER_SIZE];
		// Copying Header
		System.arraycopy(Header.tcp_sport, 0, buf, 0, 2);
		System.arraycopy(Header.tcp_dport, 0, buf, 2, 2);
		System.arraycopy(Header.tcp_seq, 0, buf, 4, 4);
		System.arraycopy(Header.tcp_ack, 0, buf, 8, 4);
		buf[12] = Header.tcp_offset;
		buf[13] = Header.tcp_flag;
		System.arraycopy(Header.tcp_window, 0, buf, 14, 2);
		System.arraycopy(Header.tcp_cksum, 0, buf, 16, 2);
		System.arraycopy(Header.tcp_urgptr, 0, buf, 18, 2);
		System.arraycopy(Header.padding, 0, buf, 20, 4);

		// Copying Data
		System.arraycopy(input, 0, buf, HEADER_SIZE, length);

		return buf;
	}

	private byte[] intToByte2(int value) {
		byte[] temp = new byte[2];
		temp[0] |= (byte) ((value & 0xFF00) >> 8);
		temp[1] |= (byte) (value & 0xFF);

		return temp;
	}

	private int byte2ToInt(byte value1, byte value2) {
		return (int)(((value1 & 0xff) << 8) | (value2 & 0xff));
	}

	// ----- Getters & Setters -----
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
	public void setUnderLayer(BaseLayer pUnderLayer) {
		if (pUnderLayer == null)
			return;
		this.p_UnderLayer = pUnderLayer;
	}

	@Override
	public void setUpperLayer(BaseLayer pUpperLayer) {
		if (pUpperLayer == null)
			return;
		this.p_aUpperLayer.add(nUpperLayerCount++, pUpperLayer);
	}

	@Override
	public void setUpperUnderLayer(BaseLayer pUULayer) {
		this.setUpperLayer(pUULayer);
		pUULayer.setUnderLayer(this);
	}
}
