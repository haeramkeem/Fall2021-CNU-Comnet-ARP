import java.util.ArrayList;

public class IPLayer implements BaseLayer {

	private static final int HEADER_SIZE = 20;
	private static final int MAX_SIZE = 1480;
	private static final int MAX_OFFSET = 185;

	// ----- Properties -----
	private int nUpperLayerCount = 0;
    private String pLayerName = null;
    private BaseLayer p_UnderLayer = null;
    private ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
    private _IP m_sHeader;

	private byte[] packetAccumulator;
	private Logger logging;
	
	private int ID = 0;

	// ----- Constructor -----
    public IPLayer(String pName) {
		pLayerName = pName;
		m_sHeader = new _IP();
		logging = new Logger(this);
	}
    
	// ----- Structures -----
    private class _IP_ADDR {
        byte[] addr = new byte[4];

        public _IP_ADDR() {
            this.addr[0] = (byte) 0x00;
            this.addr[1] = (byte) 0x00;
            this.addr[2] = (byte) 0x00;
            this.addr[3] = (byte) 0x00;
        }
    }
     
	private class _IP {			// byte	| description					| usage
		byte ip_verlen;			// 1	| IP version and header length	| !NOT USED!
		byte ip_tos;			// 1	| Type of service.				| !NOT USED!
		byte[] ip_len;			// 2	| Total packet length			| 
		byte[] ip_id;			// 2	| Datagram id					| !NOT USED!
		byte[] ip_flag_fragoff;	// 1	| Fragment flag	& frag offset	| 
		byte ip_ttl;			// 1	| Time to live in gateway hops	| !NOT USED!
		byte ip_proto;			// 1	| IP Protocol					| !NOT USED!
		byte[] ip_cksum;		// 2	| Header checksum				| !NOT USED!
		_IP_ADDR ip_src;		// 4	| Source IP address				| 
		_IP_ADDR ip_dst;		// 4	| Destination IP address		| 
		byte[] ip_data; 		// n	| Data of packet				| 


		public _IP() {
			ip_verlen = 0x45;				// !NOT USED! - IPv4, 5 * 32 = 160bit(20byte header)
			ip_tos = 0x00;					// !NOT USED! - 0 -> Normal packet
			ip_len = new byte[2];
			ip_id = new byte[2];
			ip_flag_fragoff = new byte[2];
			ip_ttl = 0x00;					// !NOT USED!
			ip_proto = 0x00;				// !NOT USED!
			ip_cksum = new byte[2];			// !NOT USED!
			ip_src = new _IP_ADDR();
			ip_dst = new _IP_ADDR();
			ip_data = null;
		}
	}
	
	// ----- Public Methods -----
	public boolean send(byte[] input, int length) {
		if(length > MAX_SIZE) {
			return sendFrag(input, length);
		} else {
			setDataHeader(length, 1, 0, 0);
			byte[] bytes = objToByte(m_sHeader, input, length);
			logging.log("Send unfragmented packet");
			return ((EthernetLayer)this.getUnderLayer()).sendData(bytes, length + HEADER_SIZE);
		}
	}

	public boolean sendARP(byte[] dstAddr) {
		logging.log("Send ARP");
		return ((ARPLayer)ChatFileDlg.m_LayerMgr.getLayer("ARP")).searchARP(m_sHeader.ip_src.addr, dstAddr);
	}

	public boolean sendGARP(byte[] srcMac) {
		logging.log("Send GARP");
		return ((ARPLayer)ChatFileDlg.m_LayerMgr.getLayer("ARP")).sendGARP(m_sHeader.ip_src.addr, srcMac);
	}

	public boolean receive(byte[] input) {
		
		_IP received = byteToObj(input, input.length);
		if(srcIsMe(received.ip_src)) {
			logging.log("Packet rejected: Sent by this host");
			return false;
		} else if(!dstIsMe(received.ip_dst)) {
			logging.log("Packet rejected: Not sent to this host");
			return false;
		}

		int d = getD(received.ip_flag_fragoff);
		int m = getM(received.ip_flag_fragoff);
		int offset = getOffset(received.ip_flag_fragoff) * 8;
		int totLen = byte2ToInt(received.ip_len[0], received.ip_len[1]);

		// If packet is not fragmented.
		if(d == 1 && m == 0) {
			logging.log("Receive unfragmented packet");
			((TCPLayer)this.getUpperLayer(0)).receive(received.ip_data);

		// If packet is fragmented.
		} else {
			//If acculator array is not set, create new byte[] to accumulate incomming data
			if(this.packetAccumulator == null || this.packetAccumulator.length != totLen) {
				this.packetAccumulator = new byte[totLen];
			}

			// If packet is first ~ last one.
			if(d == 0 && m == 1) {
				logging.log("Receive fragmented packet: offset "+offset);
				System.arraycopy(received.ip_data, 0, this.packetAccumulator, offset, MAX_SIZE);

			// If packet is last one.
			} else if(d == 0 && m == 0) {
				logging.log("Receive last fragmented packet");
				System.arraycopy(received.ip_data, 0, this.packetAccumulator, offset, totLen - offset);
				return ((TCPLayer)this.getUpperLayer(0)).receive(this.packetAccumulator);
			} else {
				logging.log("Packet rejected: Unknown D(" + d + ") and M(" + m + ")");
			}
		}
		return true;
	}
	
	// ----- Private Methods -----
	private byte[] intToByte2(int value) {
		byte[] temp = new byte[2];
		temp[0] |= (byte) ((value & 0xFF00) >> 8);
		temp[1] |= (byte) (value & 0xFF);

		return temp;
	}

	private int byte2ToInt(byte value1, byte value2) {
		return (int) (((value1 & 0xff) << 8) | (value2 & 0xff));
	}

	private void setDataHeader(int len, int D, int M, int fragoff) {
		this.m_sHeader.ip_len = intToByte2(len);
		// D : Do not fragment
		// M : More fragment
		//     if packet is not fragmented, D == 1, M == 0
		//     if packet is fragmented and is first ~ before last fragment, D == 0, M == 1
		//     if packet is fragmented and is last fragment, D == 0, M == 0
		// Here's format of ip_flag_fragoff.
		//     Size(bit):	1	1	1	13
		//     Content:		0	D	M	fragment offset
		this.m_sHeader.ip_flag_fragoff[0] = (byte) (((D & 0x1) << 6) | ((M & 0x1) << 5) | ((fragoff & 0x1f00) >> 8));
		this.m_sHeader.ip_flag_fragoff[1] = (byte) (fragoff & 0xff);
	}

	private int getD(byte[] flagFragoff) {
		return (int)((flagFragoff[0] & 0x40) >> 6);
	}

	private int getM(byte[] flagFragoff) {
		return (int)((flagFragoff[0] & 0x20) >> 5);
	}

	private int getOffset(byte[] flagFragoff) {
		return (int)(((flagFragoff[0] & 0x1f) << 8) | ((flagFragoff[1] & 0xff)));
	}

    private boolean sendFrag(byte[] input, int length) {
    	
        byte[] bytes = new byte[MAX_SIZE];

        // First fragment
		setDataHeader(length, 0, 1, 0);
        System.arraycopy(input, 0, bytes, 0, MAX_SIZE);
        bytes = objToByte(m_sHeader, bytes, MAX_SIZE);
		logging.log("Send fragmented packet: offset 0");
        ((EthernetLayer)this.getUnderLayer()).sendData(bytes, bytes.length);

        // Second ~ before last fragment
        int maxLen = length / MAX_SIZE;
        for (int i = 1; i<maxLen; i++) {
           	if (i != maxLen - 1 || length % MAX_SIZE != 0) {
				setDataHeader(length, 0, 1, i * MAX_OFFSET);
				System.arraycopy(input, MAX_SIZE*i, bytes, 0, MAX_SIZE);
				bytes = objToByte(m_sHeader, bytes, MAX_SIZE);
				logging.log("Send fragmented packet: offset "+(i * MAX_OFFSET * 8));
				((EthernetLayer)this.getUnderLayer()).sendData(bytes, bytes.length);
				try {
					Thread.sleep(4);
				} catch (InterruptedException e) {
					logging.error("Send fragment", e);
					return false;
				}
           	}
        }
        
		// Last fragment
		setDataHeader(length, 0, 0, MAX_OFFSET * maxLen);
		if(length % MAX_SIZE != 0) {
			bytes = new byte[length % MAX_SIZE];
		}
		System.arraycopy(input, length - (length%MAX_SIZE), bytes, 0, length%MAX_SIZE);
		bytes = objToByte(m_sHeader, bytes, bytes.length);
		logging.log("Send last fragmented packet");
		return ((EthernetLayer)this.getUnderLayer()).sendData(bytes, bytes.length);
    }

    private byte[] removeIPHeader(byte[] input, int length) {
	    byte[] cpyInput = new byte[length - HEADER_SIZE];
	    System.arraycopy(input, HEADER_SIZE, cpyInput, 0, length - HEADER_SIZE);
		return cpyInput;
	}

	private boolean dstIsMe(_IP_ADDR dst) {
		for (int i = 0; i < 4; i++) {
			if (m_sHeader.ip_src.addr[i] != dst.addr[i])
				return false;
		} 	
		return true;
	}
	private boolean srcIsMe(_IP_ADDR src) {
		for(int i = 0; i < 4; i++)
			if(m_sHeader.ip_src.addr[i] != src.addr[i])
				return false;
		return true;
	}
	
	// ----- Convert methods -----
	private byte[] objToByte(_IP Header, byte[] input, int length) {//data ��  �� �� 붙여주기
	    byte[] buf = new byte[length + HEADER_SIZE];

		// Copy header
		buf[0] = Header.ip_verlen;
		buf[1] = Header.ip_tos;
		System.arraycopy(Header.ip_len, 0, buf, 2, 2);
		System.arraycopy(Header.ip_id, 0, buf, 4, 2);
		System.arraycopy(Header.ip_flag_fragoff, 0, buf, 6, 2);
		buf[8] = Header.ip_ttl;
		buf[9] = Header.ip_proto;
		System.arraycopy(Header.ip_cksum, 0, buf, 10, 2);
		System.arraycopy(Header.ip_src.addr, 0, buf, 12, 4);
		System.arraycopy(Header.ip_dst.addr, 0, buf, 16, 4);

		// Copy data
		System.arraycopy(input, 0, buf, HEADER_SIZE, length);

		return buf;
	}

	private _IP byteToObj(byte[] input, int length) {
		_IP temp = new _IP();

		// Copy header
		temp.ip_verlen = input[0];
		temp.ip_tos = input[1];
		System.arraycopy(input, 2, temp.ip_len, 0, 2);
		System.arraycopy(input, 4, temp.ip_id, 0, 2);
		System.arraycopy(input, 6, temp.ip_flag_fragoff, 0, 2);
		temp.ip_ttl = input[8];
		temp.ip_proto = input[9];
		System.arraycopy(input, 10, temp.ip_cksum, 0, 2);
		System.arraycopy(input, 12, temp.ip_src.addr, 0, 4);
		System.arraycopy(input, 16, temp.ip_dst.addr, 0, 4);

		// Copy data
		temp.ip_data = this.removeIPHeader(input, length);

		return temp;
	}

	// ----- Getters & Setters -----
	public void setIPSrcAddress(byte[] srcAddress) {
	    m_sHeader.ip_src.addr = srcAddress;
	}

	public void setIPDstAddress(byte[] dstAddress) {
	    m_sHeader.ip_dst.addr = dstAddress;
	}

	public byte[] getIPSrcAddress() {
		return m_sHeader.ip_src.addr;
	}

	public byte[] getIPDstAddress() {
		return m_sHeader.ip_dst.addr;
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
