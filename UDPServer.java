import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

public class UDPServer {
    private static final int INIT_SEQ_NUM = 0;
    private static final int PACKET_SIZE = 10;
    private static final int ACK_TYPE = 3;
    private static final int NUM_PACKETS = 14; // Número total de pacotes esperados

    public static void main(String[] args) throws Exception {
        // Cria o socket UDP do servidor
        DatagramSocket serverSocket = new DatagramSocket(9876);

        byte[] receiveData = new byte[1024];
        int expectedSeqNum = INIT_SEQ_NUM;
        Map<Integer, byte[]> receivedPackets = new HashMap<>();

        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);  // Recebe o pacote UDP

            byte[] packetData = Arrays.copyOfRange(receivePacket.getData(), 0, receivePacket.getLength());
            byte type = packetData[0];
            if (type == 1) { // Pacote SYN para estabelecer conexão
                ByteBuffer wrapped = ByteBuffer.wrap(packetData, 1, 4);
                int congestionWindow = wrapped.getInt();

                // Envia SYN-ACK
                ByteBuffer buffer = ByteBuffer.allocate(5);
                buffer.put((byte) 2); // Tipo SYN-ACK
                buffer.putInt(INIT_SEQ_NUM);
                byte[] synAckData = buffer.array();
                // Cria um pacote UDP para enviar o SYN-ACK
                DatagramPacket synAckPacket = new DatagramPacket(synAckData, synAckData.length, receivePacket.getAddress(), receivePacket.getPort());
                serverSocket.send(synAckPacket);  // Envia o pacote SYN-ACK

                System.out.println("Connection established with congestion window: " + congestionWindow);
            } else if (type == 0) { // Pacote de dados
                boolean valid = verifyPacket(packetData);

                if (valid) {
                    ByteBuffer wrapped = ByteBuffer.wrap(packetData, 1, 5);
                    int seqNum = wrapped.getInt();
                    byte[] data = Arrays.copyOfRange(packetData, 13, packetData.length);

                    if (seqNum == expectedSeqNum) {
                        receivedPackets.put(seqNum, data);
                        expectedSeqNum++;
                        System.out.println("Received packet with seqNum: " + seqNum + ", data: " + Arrays.toString(data));

                        // Incrementa o número de sequência esperado
                        while (receivedPackets.containsKey(expectedSeqNum)) {
                            expectedSeqNum++;
                        }
                    } else {
                        System.out.println("Packet received out of order or duplicated, expected seqNum: " + expectedSeqNum + ", received seqNum: " + seqNum);
                    }

                    // Envia ACK
                    byte[] ackData = ByteBuffer.allocate(5).put((byte) ACK_TYPE).putInt(expectedSeqNum).array();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
                    serverSocket.send(ackPacket);  // Envia o pacote ACK
                    System.out.println("Sent ACK for seqNum: " + (expectedSeqNum - 1));
                } else {
                    System.out.println("Packet error or out of order.");
                }

                // Verifica se todos os pacotes foram recebidos e reconstituir o arquivo
                if (receivedPackets.size() == NUM_PACKETS) {
                    reassembleFile(receivedPackets);
                }
            }
        }
    }

    // Verifica a integridade do pacote usando CRC
    private static boolean verifyPacket(byte[] packetData) {
        ByteBuffer wrapped = ByteBuffer.wrap(packetData, 1, 13);
        int seqNum = wrapped.getInt();
        long receivedCrc = wrapped.getLong();

        byte[] data = Arrays.copyOfRange(packetData, 13, packetData.length);
        long computedCrc = computeCRC(data);

        return receivedCrc == computedCrc;
    }

    // Computa o CRC para verificação de integridade
    private static long computeCRC(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    // Reconstitui o arquivo a partir dos pacotes recebidos
    private static void reassembleFile(Map<Integer, byte[]> receivedPackets) throws Exception {
        System.out.println("Reassembling and saving file...");
        int fileSize = receivedPackets.size() * PACKET_SIZE;
        byte[] fileData = new byte[fileSize];

        for (Map.Entry<Integer, byte[]> entry : receivedPackets.entrySet()) {
            int seqNum = entry.getKey();
            byte[] packetData = entry.getValue();
            System.arraycopy(packetData, 0, fileData, seqNum * PACKET_SIZE, packetData.length);
        }

        // Remove bytes nulos (padding) no final do arquivo
        int actualFileSize = fileSize;
        for (int i = fileSize - 1; i >= 0; i--) {
            if (fileData[i] == 0) {
                actualFileSize--;
            } else {
                break;
            }
        }
        byte[] trimmedFileData = Arrays.copyOf(fileData, actualFileSize);

        Files.write(Paths.get("received_file"), trimmedFileData);
        System.out.println("File reassembled and saved.");

        // Calcula o hash MD5 do arquivo recebido
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] receivedHash = md.digest(trimmedFileData);
        System.out.println("Received file MD5: " + bytesToHex(receivedHash));

        // Loga o conteúdo do arquivo para comparação
        System.out.println("Received file content:");
        System.out.println(new String(trimmedFileData));
    }

    // Converte bytes para uma string hexadecimal
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
