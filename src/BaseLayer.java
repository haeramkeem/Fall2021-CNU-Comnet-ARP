interface BaseLayer {

	// Abstract methods
	public String getLayerName();

	public BaseLayer getUnderLayer();

	public BaseLayer getUpperLayer(int nindex);

	public void setUnderLayer(BaseLayer pUnderLayer);

	public void setUpperLayer(BaseLayer pUpperLayer);

	public void setUpperUnderLayer(BaseLayer pUULayer);

	// Default methods
	public default boolean send(byte[] input, int length) {
		return false;
	}

	public default boolean send(String filename) {
		return false;
	}

	public default boolean receive(byte[] input) {
		return false;
	}

	public default boolean receive() {
		return false;
	}

}
