import java.util.ArrayList;
import java.util.Arrays;

public class ChatAppLayer implements BaseLayer {

    private static final int HEADER_SIZE = 4;

    // ----- Properties -----
    private int nUpperLayerCount = 0;
    private String pLayerName = null;
    private BaseLayer p_UnderLayer = null;
    private ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
    private _CHAT_APP m_sHeader;
    private Logger logging;

    // ----- Constructor -----
    public ChatAppLayer(String pName) {
        pLayerName = pName;
        m_sHeader = new _CHAT_APP();
        logging = new Logger(this);
    }

    // ----- Structure -----
    private class _CHAT_APP {
        byte[] capp_totlen;
        byte capp_type;
        byte capp_unused;
        byte[] capp_data;

        public _CHAT_APP() {
            this.capp_totlen = new byte[2];
            this.capp_type = 0x00;
            this.capp_unused = 0x00;
            this.capp_data = null;
        }
    }

    // ----- Public Methods -----
    public boolean send(String msg) {
        byte[] bStr = msg.getBytes();
        byte[] buf = objToByte(m_sHeader, bStr, bStr.length);
        logging.log("Send");
        return ((TCPLayer)getUnderLayer()).sendChat(buf, bStr.length + HEADER_SIZE);
    }
 
    public synchronized boolean receive(byte[] input) {
        _CHAT_APP temp = byteToObj(input);
        logging.log("Receive");
        return this.getUpperLayer(0).receive(temp.capp_data);
    }

    // ----- Convert methods -----
    private byte[] objToByte(_CHAT_APP Header, byte[] input, int length) {
        byte[] buf = new byte[length + HEADER_SIZE];
        buf[0] = Header.capp_totlen[0];
        buf[1] = Header.capp_totlen[1];
        buf[2] = Header.capp_type;
        buf[3] = Header.capp_unused;
        if (length >= 0) System.arraycopy(input, 0, buf, HEADER_SIZE, length);

        return buf;
    }

    private _CHAT_APP byteToObj(byte[] input) {
        _CHAT_APP temp = new _CHAT_APP();
        temp.capp_totlen[0] = input[0];
        temp.capp_totlen[1] = input[1];
        temp.capp_type = input[2];
        temp.capp_unused = input[3];
        temp.capp_data = Arrays.copyOfRange(input, HEADER_SIZE, input.length);

        return temp;
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