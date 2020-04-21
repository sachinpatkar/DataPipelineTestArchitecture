package gov.nist.mtconnect;

import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.errors.ConnectException;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TCPSourceConnectorTest {
  public static final String IP_ADDRESS = TCPSourceTask.IP_ADDRESS;
  public static final String PORT = TCPSourceTask.PORT;
  public static final String TOPIC_CONFIG = TCPSourceTask.TOPIC_CONFIG;
  public static final String LINGER_MS = TCPSourceTask.LINGER_MS;
  public static final String BATCH_SIZE = TCPSourceTask.BATCH_SIZE;
  public static final String SPLIT_SHDR = TCPSourceTask.SPLIT_SHDR;
  public static final String MAX_CONNECTION_ATTEMPTS = TCPSourceTask.MAX_CONNECTION_ATTEMPTS;
  public static final String TIMEOUT = TCPSourceTask.TIMEOUT;

  public static final String TEST_IP_ADDRESS = "127.0.0.1";
  public static final String TEST_PORT = "7878";
  public static final String TEST_TOPIC_CONFIG = "VMC-3Axis_SHDR";
  public static final String TEST_LINGER_MS = "10000";
  public static final String TEST_BATCH_SIZE = "100";
  public static final String TEST_SPLIT_SHDR = "true";
  public static final String TEST_CONNECTION_ATTEMPTS = "5";
  public static final String TEST_TIMEOUT = "60000";

  @Test
  public void testInitialConfigs() {
    Map<String,String> parsedConfigs = new HashMap<>();
    parsedConfigs.put(IP_ADDRESS, TEST_IP_ADDRESS);
    parsedConfigs.put(PORT, TEST_PORT);
    parsedConfigs.put(TOPIC_CONFIG, TEST_TOPIC_CONFIG);
    parsedConfigs.put(LINGER_MS, TEST_LINGER_MS);
    parsedConfigs.put(BATCH_SIZE, TEST_BATCH_SIZE);
    parsedConfigs.put(SPLIT_SHDR, TEST_SPLIT_SHDR);


    TCPSourceConnector connector = new TCPSourceConnector();
    connector.start(parsedConfigs);
    List<Map<String, String>> configs = connector.taskConfigs(2);
    System.out.println(configs.get(0));
  }
  @Test
  public void testWithTasks() throws InterruptedException {
    Map<String,String> parsedConfigs = new HashMap<>();
    parsedConfigs.put(IP_ADDRESS, TEST_IP_ADDRESS);
    parsedConfigs.put(PORT, TEST_PORT);
    parsedConfigs.put(TOPIC_CONFIG, TEST_TOPIC_CONFIG);
    parsedConfigs.put(LINGER_MS, TEST_LINGER_MS);
    parsedConfigs.put(BATCH_SIZE, TEST_BATCH_SIZE);
    parsedConfigs.put(SPLIT_SHDR, TEST_SPLIT_SHDR);
    parsedConfigs.put(MAX_CONNECTION_ATTEMPTS, TEST_CONNECTION_ATTEMPTS);
    parsedConfigs.put(TIMEOUT, TEST_TIMEOUT);


    TCPSourceConnector connector = new TCPSourceConnector();
    connector.start(parsedConfigs);
    List<Map<String, String>> configs = connector.taskConfigs(2);

    TCPSourceTask task = new TCPSourceTask();
    task.start(configs.get(0));
    List<SourceRecord> records = task.poll();
    task.stop();

    for (int i =0 ; i < records.size(); i++){
      System.out.println(records.get(i));
    }
  }
  @Test (expected = ConnectException.class)
  public void testGracefulFail() throws InterruptedException {
    Map<String,String> parsedConfigs = new HashMap<>();
    parsedConfigs.put(IP_ADDRESS, TEST_IP_ADDRESS);
    parsedConfigs.put(PORT, TEST_PORT);
    parsedConfigs.put(TOPIC_CONFIG, TEST_TOPIC_CONFIG);
    parsedConfigs.put(LINGER_MS, TEST_LINGER_MS);
    parsedConfigs.put(BATCH_SIZE, TEST_BATCH_SIZE);
    parsedConfigs.put(SPLIT_SHDR, TEST_SPLIT_SHDR);
    parsedConfigs.put(MAX_CONNECTION_ATTEMPTS, "4");
    parsedConfigs.put(TIMEOUT, TEST_TIMEOUT);

    MockAdapterService mockAdapterService = new MockAdapterService();
    new Thread(mockAdapterService).start();

    TCPSourceConnector connector = new TCPSourceConnector();
    connector.start(parsedConfigs);
    List<Map<String, String>> configs = connector.taskConfigs(2);

    TCPSourceTask task = new TCPSourceTask();
    task.start(configs.get(0));
    while(true) {
      List<SourceRecord> records = task.poll();
      if (!records.isEmpty()){System.out.println(records.get(0));}
      //else {task.start(configs.get(0));}
    }
  }
}
