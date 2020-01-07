package tomcat.request.session;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SessionMetadata implements Serializable {
    private static final long serialVersionUID = 124438185184833546L;
    private byte[] attributesHash = new byte[0];

    public SessionMetadata() {
    }

    public byte[] getAttributesHash() {
        return this.attributesHash;
    }

    public void setAttributesHash(byte[] attributesHash) {
        this.attributesHash = attributesHash;
    }

    public void copyFieldsFrom(SessionMetadata metadata) {
        this.setAttributesHash(metadata.getAttributesHash());
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(this.attributesHash.length);
        out.write(this.attributesHash);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int hashLength = in.readInt();
        byte[] attributesHash = new byte[hashLength];
        in.read(attributesHash, 0, hashLength);
        this.attributesHash = attributesHash;
    }
}
