import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.CRC32;

public class UDPClient {
    private static final int PACKET_SIZE = 10;

    public static void main(String[] args) throws Exception {
        // Defina o caminho do arquivo diretamente aqui
        String filePath = "C:\\Users\\giova\\Downloads\\teste trab redes\\arquivo.txt";  // Atualize este caminho conforme necessário
        String serverIp = "127.0.0.1";  // IP de loopback para localhost

        byte[] fileData = Files.readAllBytes(Paths.get(filePath));

        // Calcula o hash MD5 do arquivo original
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] originalHash = md.digest(fileData);
        System.out.println("Original file MD5: " + bytesToHex(originalHash));

        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName(serverIp);

        int congestionWindow = 1;
        int threshold = 64;  // Valor arbitrário para o limiar
        boolean slowStart = true;
        int seqNum = 0;

        // Estabelece a conexão
        System.out.println("Establishing connection...");
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put((byte) 1); // Tipo SYN
        buffer.putInt(threshold);
        byte[] synData = buffer.array();
        DatagramPacket synPacket = new DatagramPacket(synData, synData.length, IPAddress, 9876);
        clientSocket.send(synPacket);

        // Recebe SYN-ACK
        byte[] receiveData = new byte[5];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);

        if (receiveData[0] == 2) { // Tipo SYN-ACK
            ByteBuffer wrapped = ByteBuffer.wrap(receivePacket.getData(), 1, 4);
            int initSeqNum = wrapped.getInt();
            seqNum = initSeqNum;
            System.out.println("Connection established.");

            // Divide o arquivo em pacotes de 10 bytes
            int numPackets = (fileData.length + PACKET_SIZE - 1) / PACKET_SIZE;
            System.out.println("Starting to send file...");

            while (seqNum < numPackets) {
                for (int i = 0; i < congestionWindow && seqNum < numPackets; i++) {
                    int start = seqNum * PACKET_SIZE;
                    int end = Math.min(start + PACKET_SIZE, fileData.length);
                    byte[] packetData = new byte[PACKET_SIZE];
                    Arrays.fill(packetData, (byte) 0); // Preenche com zeros para garantir o padding correto
                    System.arraycopy(fileData, start, packetData, 0, end - start);

                    byte[] sendData = createPacket(seqNum, packetData);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9876);
                    clientSocket.send(sendPacket);
                    System.out.println("Sent packet with seqNum: " + seqNum + ", data: " + Arrays.toString(packetData));
                    seqNum++;
                }

                receivePacket = new DatagramPacket(receiveData, receiveData.length);

                try {
                    clientSocket.setSoTimeout(1000);
                    clientSocket.receive(receivePacket);

                    ByteBuffer ackWrapped = ByteBuffer.wrap(receivePacket.getData(), 1, 4);
                    int ackNum = ackWrapped.getInt();
                    System.out.println("Received ACK for seqNum: " + (ackNum - 1));

                    if (receivePacket.getData()[0] == 3 && ackNum == seqNum) { // Tipo ACK
                        if (slowStart) {
                            // Implementação do Slow Start
                            congestionWindow *= 2;  // Aumenta a janela de congestionamento exponencialmente
                            if (congestionWindow >= threshold) {
                                slowStart = false;  // Transição para Congestion Avoidance
                            }
                        } else {
                            // Implementação do Congestion Avoidance
                            congestionWindow++;  // Aumenta a janela de congestionamento linearmente
                        }
                    } else {
                        // Se ocorrer um timeout ou um erro, redefine a janela de congestionamento
                        threshold = Math.max(congestionWindow / 2, 1);
                        congestionWindow = 1;
                        slowStart = true;
                    }
                } catch (Exception e) {
                    // Tratamento de timeout
                    System.out.println("Timeout occurred, retransmitting from seqNum: " + (seqNum - congestionWindow));
                    seqNum -= congestionWindow;
                    congestionWindow = 1;
                    slowStart = true;
                }

                Thread.sleep(500);
            }

            System.out.println("Finished sending file.");
        }

        clientSocket.close();  // Fecha o socket do cliente
    }

    // Cria um pacote com o número de sequência e os dados
    private static byte[] createPacket(int seqNum, byte[] data) {
        long crc = computeCRC(data);

        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 8 + data.length);
        buffer.put((byte) 0); // Tipo de pacote de dados
        buffer.putInt(seqNum);
        buffer.putLong(crc);
        buffer.put(data);

        return buffer.array();
    }

    // Computa o CRC para verificação de integridade
    private static long computeCRC(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
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
