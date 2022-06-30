public class Logger {
    private BaseLayer target;

    public Logger(BaseLayer target) {
        this.target = target;
    }

    public void log(String msg) {
        System.out.println("["+this.target.getLayerName()+"] : "+msg);
    }

    public void panic(String err, Exception e) {
        System.err.println("["+this.target.getLayerName()+"] : "+err+" "+e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }

    public void error(String err) {
        System.err.println("["+this.target.getLayerName()+"] : "+err);
    }

    public void error(String err, Exception e) {
        System.err.println("["+this.target.getLayerName()+"] : "+err+" "+e.getMessage());
        e.printStackTrace();
    }
}
