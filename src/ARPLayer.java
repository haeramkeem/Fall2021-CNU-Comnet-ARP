import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ARPLayer implements BaseLayer {

	// ----- Property -----
	private int nUpperLayerCount = 0;
	private String pLayerName = null;
	private BaseLayer p_UnderLayer = null;
	private ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
	private _ARP m_sHeader;

	private Logger logging;

	// ----- ARPTable -----
	private HashMap<String, String> ARPTable = new HashMap<>();

	// ----- ProxyARPTable -----
	private HashMap<String, String> ProxyARPTable = new HashMap<>();

	// ----- ARPTimeTable -----
	private HashMap<String, Long> ARPTimeTable = new HashMap<>();

	// ----- Constructor -----
	public ARPLayer(String pName) {
		pLayerName = pName;
		m_sHeader = new _ARP();
		logging = new Logger(this);
	}

	// ----- Structure -----
	private class _ARP {
		byte[] arp_hard_type;
		byte[] arp_prot_type;
		byte arp_hard_size;
		byte arp_prot_size;
		byte[] arp_op;
		byte[] arp_enet_srcaddr;
		byte[] arp_ip_srcaddr;
		byte[] arp_enet_dstaddr;
		byte[] arp_ip_dstaddr;

		public _ARP() {
			this.arp_hard_type = new byte[2];
			this.arp_prot_type = new byte[2];
			this.arp_hard_size = 0x00;
			this.arp_prot_size = 0x00;
			this.arp_op = new byte[2];
			this.arp_enet_srcaddr = new byte[6];
			this.arp_ip_srcaddr = new byte[4];
			this.arp_enet_dstaddr = new byte[6];
			this.arp_ip_dstaddr = new byte[4];
		}
	}

	// Methods
	private void setHeaderToEnetAndIP() {
		m_sHeader.arp_hard_type = intToByte2(0x0001); // 0x0001 means Ethernet
		m_sHeader.arp_prot_type = intToByte2(0x0800); // 0x0800 means IP
		m_sHeader.arp_hard_size = 0x06; // length of Ethernet address -> 6 byte
		m_sHeader.arp_prot_size = 0x04; // length of IP address -> 4 byte
	}
	
	// ----- getDstMac -----
	public byte[] getDstMac(byte[] srcIP, byte[] dstIP) {
		deleteTimeOverARP();
		logging.log("Get destination MAC addr requested");
		String strDstIP = ByteToString(dstIP);
		if(ARPTable.containsKey(strDstIP) && ARPTable.get(strDstIP) != "??????") {
			return macStoB(ARPTable.get(strDstIP));
		}
		searchARP(srcIP, dstIP);
		while (!ARPTable.containsKey(strDstIP) || ARPTable.get(strDstIP) == "??????") {
			try {
				Thread.sleep(4);
			} catch (InterruptedException e) {
				logging.error("Get destination MAC Addr", e);
				return null;
			}
		}
		return macStoB(ARPTable.get(strDstIP));
	}
	
	// ---- deleteARPTable -----
	public void deleteARPTable(byte[] deleteIP, int AllORItem) {
		if(AllORItem == 0) {
			logging.log("Delete all ARP table elements requested");
			ARPTable.clear();
			((ChatFileDlg) ChatFileDlg.m_LayerMgr.getLayer("GUI")).printARPTable(ARPTable);
		}
		else {
			if(ARPTable.containsKey(ByteToString(deleteIP))) {
				logging.log("Delete one ARP table element requested");
				ARPTable.remove(ByteToString(deleteIP));
				((ChatFileDlg) ChatFileDlg.m_LayerMgr.getLayer("GUI")).printARPTable(ARPTable);
			}
		}
	}
	// ---- deleteProxyTable -----	
	public void deleteProxyTable(byte[] deleteIP) {
		if(ProxyARPTable.containsKey(ByteToString(deleteIP))) {
			logging.log("Delete one Proxy table element requested");
			ProxyARPTable.remove(ByteToString(deleteIP));
			((ChatFileDlg) ChatFileDlg.m_LayerMgr.getLayer("GUI")).printProxyTable(ProxyARPTable);
		}
	}
	
	// ----- deleteTimeOverARP -----
	public void deleteTimeOverARP() {
		for (Map.Entry<String, Long> entry : ARPTimeTable.entrySet()) {
			long now = System.currentTimeMillis ();
			long elapsedTime = ( now - entry.getValue() ) / 1000;
			if (ARPTable.get(entry.getKey()).equals("??????") && elapsedTime >= 180) {
				logging.log("Delete expired ARP Table element");
				ARPTable.remove(entry.getKey());
			}
			else if (!ARPTable.get(entry.getKey()).equals("??????") && elapsedTime >= 180) {
				ARPTimeTable.remove(entry.getKey());
			}
		}
		((ChatFileDlg) ChatFileDlg.m_LayerMgr.getLayer("GUI")).printARPTable(ARPTable);
	}
	// ----- setProxyTable -----
	public void setProxyTable(byte[] IP_ADDR, byte[] MAC_ADDR) {
		logging.log("Set one Proxy table element requested");
		String ip_addr = ByteToString(IP_ADDR);
		String mac_addr = macBtoS(MAC_ADDR);
		ProxyARPTable.put(ip_addr, mac_addr);
		((ChatFileDlg) ChatFileDlg.m_LayerMgr.getLayer("GUI")).printProxyTable(ProxyARPTable);
		
	}
	
	// ----- sendGARP -----
	public boolean sendGARP(byte[] srcIP, byte[] srcMac) {
		this.setHeaderToEnetAndIP();
		m_sHeader.arp_op = intToByte2(0x0001);
		m_sHeader.arp_enet_srcaddr = srcMac;
		m_sHeader.arp_ip_srcaddr = srcIP;
		m_sHeader.arp_enet_dstaddr = new byte[6];
		m_sHeader.arp_ip_dstaddr = srcIP;
		byte[] bytes = objToByte(m_sHeader);
		logging.log("Send GARP");
		return ((EthernetLayer) getUnderLayer()).sendARP(bytes, 28);
	}
	// ----- searchARP -----
	public boolean searchARP(byte[] srcIP, byte[] dstIP) {
		deleteTimeOverARP();
		String dstIP_String = ByteToString(dstIP);
		// exist ARPTable
		if (ARPTable.containsKey(dstIP_String) && ARPTable.get(dstIP_String) != "??????") {
			return true;
		}
		// none exist ARPTable
		else {
			ARPTable.put(dstIP_String, "??????");
			ARPTimeTable.put(dstIP_String, System.currentTimeMillis());
			((ChatFileDlg) ChatFileDlg.m_LayerMgr.getLayer("GUI")).printARPTable(ARPTable);
			
			// ----- ARPRequest -----
			this.setHeaderToEnetAndIP();
			m_sHeader.arp_op = intToByte2(0x0001);
			m_sHeader.arp_enet_srcaddr = ((EthernetLayer) getUnderLayer()).getEnetSrcAddress();
			m_sHeader.arp_ip_srcaddr = srcIP;
			m_sHeader.arp_enet_dstaddr = new byte[6];
			m_sHeader.arp_ip_dstaddr = dstIP;
			byte[] bytes = objToByte(m_sHeader);
			logging.log("Send ARP Request");
			return ((EthernetLayer) getUnderLayer()).sendARP(bytes, 28);
		}
	}

	public boolean sendReply(byte[] srcMacAddr, byte[] srcIpAddr, byte[] dstMacAddr, byte[] dstIpAddr) {
		this.setHeaderToEnetAndIP();
		m_sHeader.arp_op = intToByte2(0x0002);
		m_sHeader.arp_enet_srcaddr = dstMacAddr;
		m_sHeader.arp_ip_srcaddr = dstIpAddr;
		m_sHeader.arp_enet_dstaddr = srcMacAddr;
		m_sHeader.arp_ip_dstaddr = srcIpAddr;
		byte[] bytes = objToByte(m_sHeader);
		logging.log("Send ARP Reply");
		return ((EthernetLayer) getUnderLayer()).sendARP(bytes, 28);
	}

	public synchronized boolean receive(byte[] input) {
		deleteTimeOverARP();
		byte[] srcMacAddr = Arrays.copyOfRange(input, 8, 14);
		byte[] srcIpAddr = Arrays.copyOfRange(input, 14, 18);
		byte[] dstMacAddr = Arrays.copyOfRange(input, 18, 24);
		byte[] dstIpAddr = Arrays.copyOfRange(input, 24, 28);
		int requestORreply = byte2ToInt(input[6], input[7]);
		
		// src ARP auto upload
		ARPTable.put(ByteToString(srcIpAddr), macBtoS(srcMacAddr));
		((ChatFileDlg) ChatFileDlg.m_LayerMgr.getLayer("GUI")).printARPTable(ARPTable);
		
		// isMine 
		if (isMine(dstIpAddr)) {
			// ARP request
			if (requestORreply == 1) {
				// GARP receive
				if (srcIpAddr == dstIpAddr) {
					logging.log("Receive GARP");
					return true;
				}
				logging.log("Receive ARP Request");
				dstMacAddr = ((EthernetLayer) getUnderLayer()).getEnetSrcAddress();
				return sendReply(srcMacAddr, srcIpAddr, dstMacAddr, dstIpAddr);
			}
			// ARP reply
			else if (requestORreply == 2) {
				logging.log("Receive ARP Reply");
				return true;
			}
		}
		// not Mine 
		else if (srcIpAddr != dstIpAddr) {
			// proxy table check
			String dstIP_String = ByteToString(dstIpAddr);
			// exist proxy table
			if (ProxyARPTable.containsKey(dstIP_String)) {
				logging.log("Receive ARP for proxy host");
				byte[] proxy_dstMacAddr = ((EthernetLayer) getUnderLayer()).getEnetSrcAddress();
				sendReply(srcMacAddr, srcIpAddr, proxy_dstMacAddr, StringToByte(dstIP_String));
				return true;
			}
			// none exist proxy table
			else {
				logging.log("Receive ARP that is not mine");
				return false;
			}
		}
		return true;
	}

	
	private boolean isMine(byte[] input) {
		byte[] MyADDR = ((IPLayer) getUpperLayer(0)).getIPSrcAddress();
		for (int i = 0; i < 4; i++)
			if (MyADDR[i] != input[i])
				return false;
		return true;
	}

	// ----- Convert methods -----
	public byte[] objToByte(_ARP Header) {
		byte[] buf = new byte[28];

		// Copying Data
		System.arraycopy(Header.arp_hard_type, 0, buf, 0, 2);
		System.arraycopy(Header.arp_prot_type, 0, buf, 2, 2);
		buf[4] = Header.arp_hard_size;
		buf[5] = Header.arp_prot_size;
		System.arraycopy(Header.arp_op, 0, buf, 6, 2);
		System.arraycopy(Header.arp_enet_srcaddr, 0, buf, 8, 6);
		System.arraycopy(Header.arp_ip_srcaddr, 0, buf, 14, 4);
		System.arraycopy(Header.arp_enet_dstaddr, 0, buf, 18, 6);
		System.arraycopy(Header.arp_ip_dstaddr, 0, buf, 24, 4);
		return buf;
	}

	public _ARP byteToObj(byte[] buf) {
		_ARP temp = new _ARP();

		// Copying Data
		System.arraycopy(buf, 0, temp.arp_hard_type, 0, 2);
		System.arraycopy(buf, 2, temp.arp_prot_type, 0, 2);
		temp.arp_hard_size = buf[4];
		temp.arp_prot_size = buf[5];
		System.arraycopy(buf, 6, temp.arp_op, 0, 2);
		System.arraycopy(buf, 8, temp.arp_enet_srcaddr, 0, 6);
		System.arraycopy(buf, 14, temp.arp_ip_srcaddr, 0, 4);
		System.arraycopy(buf, 18, temp.arp_enet_dstaddr, 0, 6);
		System.arraycopy(buf, 24, temp.arp_ip_dstaddr, 0, 4);
		return temp;
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

	// ----- ByteToString -----
	private String ByteToString(byte[] data) {
		return String.format("%d.%d.%d.%d", (data[0] & 0xff), (data[1] & 0xff), (data[2] & 0xff), (data[3] & 0xff));
	}

	private String macBtoS(byte[] b) {
		return String.format("%02X-%02X-%02X-%02X-%02X-%02X", b[0], b[1], b[2], b[3], b[4], b[5]);
	}

	// ----- StringToByte -----
	private byte[] StringToByte(String data) {
		String[] strArr = data.split("[.]");
		if(strArr.length != 4) { return null; }
		byte[] byteIpAddr = new byte[4];
		for(int i = 0; i < 4; i++) {
			byteIpAddr[i] = (byte)Integer.parseInt(strArr[i]);
		}
		return byteIpAddr;
	}

	private byte[] macStoB(String strMacAddr) {
		String[] strArr = strMacAddr.split("-");
		if(strArr.length != 6) { return null; }
		byte[] byteMacAddr = new byte[6];
		for(int i = 0; i < 6; i++) {
			byteMacAddr[i] = (byte)Integer.parseInt(strArr[i], 16);
		}
		return byteMacAddr;
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
