import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.ByteBuffer;

public class FileAppLayer implements BaseLayer {

    private static final int HEADER_SIZE = 7;
    private static final int FILEINFO_TYPE = 0;
    private static final int FILEDATA_TYPE = 1;

    // ----- Properties -----
    private int nUpperLayerCount = 0;
    private String pLayerName = null;
    private BaseLayer p_UnderLayer = null;
    private ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
    private _FAPP_HEADER m_sHeader;

    private File fileAcc;
    private int fileAccSize;
    private Logger logging;

    // ----- Constructor -----
    public FileAppLayer(String pName) {
        pLayerName = pName;
        m_sHeader = new _FAPP_HEADER();
        logging = new Logger(this);
    }

    // ----- Structures -----
    public class _FAPP_HEADER {
        byte[] fapp_totlen;
        byte[] fapp_type;
        byte fapp_unused;
        byte[] fapp_data;

        public _FAPP_HEADER() {
            this.fapp_totlen = new byte[4];
            this.fapp_type = new byte[2];
            this.fapp_unused = 0x00;
            this.fapp_data = null;
        }
    }

    // ----- Public methods -----
    public boolean send(File file) {
        try (FileInputStream fs = new FileInputStream(file)) {
            // Initiate
            BufferedInputStream bs = new BufferedInputStream(fs);
            int fileSize = (int)file.length();
            ((ChatFileDlg)this.getUpperLayer(0)).progressBar.setMinimum(0);
            ((ChatFileDlg)this.getUpperLayer(0)).progressBar.setMaximum(fileSize);
            ((ChatFileDlg)this.getUpperLayer(0)).progressBar.setValue(0);
            setTotLen(fileSize);

            // Send file information
            setType(FILEINFO_TYPE);
            byte[] data = file.getName().getBytes();
            logging.log("Send file info");
            sendToUnderLayer(data, data.length);

            // Send file data
            setType(FILEDATA_TYPE);
            data = new byte[fileSize];
            bs.read(data);
            logging.log("Send file data");
            sendToUnderLayer(data, fileSize);

            // Finalize
            ((ChatFileDlg)this.getUpperLayer(0)).progressBar.setValue(fileSize);
            fs.close();
            bs.close();
        } catch(IOException e) {
            logging.error("Send:", e);
            return false;
        }
        return true;
    }

    public boolean receive(byte[] input) {
        _FAPP_HEADER received = this.byteToObj(input);
        int type = byte2ToInt(received.fapp_type[0], received.fapp_type[1]);
        if(type == FILEINFO_TYPE) {
            // Make file
            String fileName = new String(received.fapp_data).trim();
            this.fileAcc = new File("./" + fileName);

            // Save file size to validate
            this.fileAccSize = byte4Toint(received.fapp_totlen);

            // Initiate Progress bar
            ((ChatFileDlg)this.getUpperLayer(0)).progressBar.setMinimum(0);
            ((ChatFileDlg)this.getUpperLayer(0)).progressBar.setMaximum(this.fileAccSize);
            ((ChatFileDlg)this.getUpperLayer(0)).progressBar.setValue(0);
            logging.log("Receive file info");
        } else if(type == FILEDATA_TYPE) {
            try(FileOutputStream fos = new FileOutputStream(this.fileAcc)) {
                // Validate data size
                if(received.fapp_data.length != this.fileAccSize) {
                    throw new Exception("File size mismatch");
                }

                // Write file
                fos.write(received.fapp_data);

                // Finalize
                ((ChatFileDlg)this.getUpperLayer(0)).progressBar.setValue(this.fileAccSize);
                ((ChatFileDlg)this.getUpperLayer(0)).ChattingArea.append("Success\n");
                fos.close();
                logging.log("Receive file data");
            } catch (Exception e) {
                logging.error("Receive:", e);
                return false;
            }
        }
        return true;
    }

    // ----- Private methods -----
    private boolean sendToUnderLayer(byte[] input, int length) {
        byte[] bytes = this.objToByte(m_sHeader, input, length);
        return ((TCPLayer)this.getUnderLayer()).sendFile(bytes, length + HEADER_SIZE);
    }

    private byte[] objToByte(_FAPP_HEADER header, byte[] input, int length) {
        byte[] buf = new byte[length + HEADER_SIZE];
        System.arraycopy(header.fapp_totlen, 0, buf, 0, 4);
        System.arraycopy(header.fapp_type, 0, buf, 4, 2);
        buf[6] = header.fapp_unused;
        System.arraycopy(input, 0, buf, HEADER_SIZE, length);

        return buf;
    }

    private _FAPP_HEADER byteToObj(byte[] input) {
        _FAPP_HEADER temp = new _FAPP_HEADER();
        System.arraycopy(input, 0, temp.fapp_totlen, 0, 4);
        System.arraycopy(input, 4, temp.fapp_type, 0, 2);
        temp.fapp_unused = input[6];
        temp.fapp_data = Arrays.copyOfRange(input, HEADER_SIZE, input.length);
        return temp;
    }

    private void setTotLen(int totlen) {
        this.m_sHeader.fapp_totlen = this.intToByte4(totlen);
    }

    private void setType(int type) {
        this.m_sHeader.fapp_type = this.intToByte2(type);
    }

    private byte[] intToByte2(int value) {
		byte[] temp = new byte[2];
		temp[0] |= (byte) ((value & 0xFF00) >> 8);
		temp[1] |= (byte) (value & 0xFF);

		return temp;
	}

	private int byte2ToInt(byte value1, byte value2) {
		return (int) (((value1 & 0xff) << 8) | (value2 & 0xff));
	}

    private byte[] intToByte4(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
    }

    private int byte4Toint(byte[] value) {
    	return ByteBuffer.wrap(value).getInt();
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
        if(nindex < 0 || nindex > nUpperLayerCount || nUpperLayerCount < 0)
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
        if(pUpperLayer == null)
            return;
        this.p_aUpperLayer.add(nUpperLayerCount++, pUpperLayer);
    }

    @Override
    public void setUpperUnderLayer(BaseLayer pUULayer) {
        this.setUpperLayer(pUULayer);
        pUULayer.setUnderLayer(this);
    }
}