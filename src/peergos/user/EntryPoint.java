package peergos.user;

import peergos.corenode.*;
import peergos.crypto.*;
import peergos.crypto.symmetric.*;
import peergos.user.fs.*;
import peergos.util.*;

import java.io.*;
import java.util.*;

public class EntryPoint {

    public final ReadableFilePointer pointer;
    public final String owner;
    public final Set<String> readers, writers;

    public EntryPoint(ReadableFilePointer pointer, String owner, Set<String> readers, Set<String> writers) {
        this.pointer = pointer;
        this.owner = owner;
        this.readers = readers;
        this.writers = writers;
    }

    public byte[] serializeAndEncrypt(User user, UserPublicKey target) throws IOException {
        return target.encryptMessageFor(this.serialize(), user.secretBoxingKey);
    }

    public byte[] serializeAndSymmetricallyEncrypt(SymmetricKey key) throws IOException {
        byte[] nonce = key.createNonce();
        return ArrayOps.concat(nonce, key.encrypt(this.serialize(), nonce));
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        Serialize.serialize(pointer.toByteArray(), dout);
        Serialize.serialize(owner, dout);
        dout.writeInt(readers.size());
        readers.forEach(s -> {try {Serialize.serialize(s, dout);} catch (IOException e){}});
        dout.writeInt(writers.size());
        writers.forEach(s -> {try {Serialize.serialize(s, dout);} catch (IOException e){}});
        return bout.toByteArray();
    }

    static EntryPoint symmetricallyDecryptAndDeserialize(byte[] input, SymmetricKey key) throws IOException {
        byte[] nonce = Arrays.copyOfRange(input, 0, 24);
        byte[] raw = key.decrypt(Arrays.copyOfRange(input, 24, input.length), nonce);
        DataInputStream din = new DataInputStream(new ByteArrayInputStream(raw));
        ReadableFilePointer pointer = ReadableFilePointer.fromByteArray(Serialize.deserializeByteArray(din, 4*1024*1024));
        String owner = Serialize.deserializeString(din, CoreNode.MAX_USERNAME_SIZE);
        int nReaders = din.readInt();
        Set<String> readers = new HashSet<>();
        for (int i=0; i < nReaders; i++)
            readers.add(Serialize.deserializeString(din, CoreNode.MAX_USERNAME_SIZE));
        int nWriters = din.readInt();
        Set<String> writers = new HashSet<>();
        for (int i=0; i < nWriters; i++)
            writers.add(Serialize.deserializeString(din, CoreNode.MAX_USERNAME_SIZE));
        return new EntryPoint(pointer, owner, readers, writers);
    }
}