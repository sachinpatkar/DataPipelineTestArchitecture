package gov.nist.mtconnect;

import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;



import org.w3c.dom.*;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;


public class MTConnectSourceTask extends SourceTask {
  static final Logger log = LoggerFactory.getLogger(MTConnectSourceTask.class);

  public static final String AGENT_URL = "agent_url";
  public static final String DEVICE_PATH = "device_path";
  public static final String NEXT_SEQUENCE = "next_sequence";
  public static final String TOPIC_CONFIG = "topic_config";

  private String agentURL; // should be input from config
  private String devicePath;
  private String topic;
  private Integer nextSequence = -1;

  private  XPathFactory xPathFactory = null;
  private  XPath xPath = null;

  @Override
  public String version() {
    return VersionUtil.getVersion();
  }

  @Override
  public void start(final Map<String, String> props) {
    //TODO: Do things here that are required to start your task. This could be open a connection to a database, etc.
      this.agentURL = props.get(MTConnectSourceTask.AGENT_URL);
      this.devicePath = props.get(MTConnectSourceTask.DEVICE_PATH);
      //nextSequence = Integer.parseInt(props.get(NEXT_SEQUENCE));
      this.topic = props.get(MTConnectSourceTask.TOPIC_CONFIG);

      MTConnectSourceTask.log.debug("Trying to get persistedMap.");

      Map<String, Object> persistedMap = null;
      if (this.context != null && this.context.offsetStorageReader() != null) {
          // Lookup the offset by the device url (the key is stored as a singletonMap)
          persistedMap = this.context.offsetStorageReader().offset(Collections.singletonMap(MTConnectSourceTask.AGENT_URL, this.agentURL));
      }
      //log.info("The persistedMap is {}", persistedMap);
      if (persistedMap != null) {
          nextSequence = (Integer) persistedMap.get(MTConnectSourceTask.NEXT_SEQUENCE);
      }

      this.xPathFactory = XPathFactory.newInstance();
      this.xPath = this.xPathFactory.newXPath();

  }

  @Override
  public List<SourceRecord> poll() {
      final Document xmlResponseDocument = getXMLResponseDocument();
      List<SourceRecord> records = Collections.emptyList();

      try {
          if (xmlResponseDocument != null) {
              final String xmlResponseDocumentType = xmlResponseDocument.getDocumentElement().getNodeName();
              System.out.println("Response Document Type: " + xmlResponseDocumentType);
              if (xmlResponseDocumentType.equals("MTConnectStreams")) {
                  // Store the Next Sequence Number
                  XPathExpression expr = this.xPath.compile("//*/@nextSequence");
                  nextSequence = Integer.parseInt((String) expr.evaluate(xmlResponseDocument, XPathConstants.STRING));
                  //Check that the streams aren't empty
                  expr = this.xPath.compile("//*[child::Streams]");
                  NodeList streamsChildList = (NodeList) expr.evaluate(xmlResponseDocument, XPathConstants.NODESET);
                  // Put response document into SourceRecord
                  if (streamsChildList.getLength() >0) {getResponseDocumentAsSourceRecords(xmlResponseDocument);}
              } else if (xmlResponseDocumentType.equals("MTConnectError")) {
                  // Else If Response is MTConnect Error
                  nextSequence = -1;
                  System.out.println("Response Document Type: " + xmlResponseDocumentType);
              }
          } else {
              // Else (didn't get an MTConnect response at all)
              nextSequence = -1;
              System.out.println("No Valid Response Document");
          }
      }
      catch(XPathExpressionException e) {
          e.printStackTrace();
      }


      return records;
  }

    @Override
  public void stop() {
    //TODO: Do whatever is required to stop your task.
  }


    private Document getXMLResponseDocument() {
        final HttpURLConnection urlConnection;
        String urlString;
        final URL url;
        InputStream responseStream = null;
        // Query the last known NextSequence Number
        // if nextSequence == -1, get Current
        try {
            if (nextSequence == -1) {
                urlString = agentURL + "/current?";
            }
            else if(nextSequence != -1){
                urlString = agentURL +"/sample?from=" + nextSequence;
            }
            else { //else append nextSequence to http
                urlString = agentURL + "/current?";
            }

            if (devicePath != null){
                urlString += "&"+ devicePath;
            }
            System.out.println(urlString);
            url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("Accept", "application/xml");

            //Check the url connection
            final int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                //If okay, get inputstream from connection
                responseStream = urlConnection.getInputStream();
                // parse stream if it's not null
                if (responseStream != null){return parseStream(responseStream);}
                else {
                    System.out.println("Not Returning MTConnect (HTTP Okay though?)");
                    return null;
                }
            } else {
                System.out.println("Not Returning MTConnect (HTTP Connection Issue)");
                return null;
            }
        } catch (final MalformedURLException | ProtocolException e) {
            e.printStackTrace();
        } catch (final IOException e){
            e.printStackTrace(); //url.Connection.getResponseCode
        }

        return null;
    }

    private List<SourceRecord> getResponseDocumentAsSourceRecords(final Document xmlResponseDocument) {
      final ArrayList<SourceRecord> records = new ArrayList<>();
      final Map<String, String> sourcePartition = new HashMap<String, String>();
        sourcePartition.put(MTConnectSourceTask.AGENT_URL, agentURL);
        sourcePartition.put(MTConnectSourceTask.DEVICE_PATH, devicePath);
      final Map<String, Integer> sourceOffset = Collections.singletonMap(MTConnectSourceTask.NEXT_SEQUENCE, this.nextSequence);
      final String value = DOMtoStringWriter(xmlResponseDocument).toString();
      records.add(new SourceRecord(sourcePartition, sourceOffset, this.topic, null, value));

      return records;
    }

    private Document parseStream(final InputStream responseStream) {

        try {
            final Document xmlResponseDocument = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(responseStream);
            return xmlResponseDocument;

        } catch (final ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private StringWriter DOMtoStringWriter(final Document xmlResponseDocument){
        final StringWriter writer = new StringWriter();
        try {
            final TransformerFactory tf = TransformerFactory.newInstance();
            final Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(xmlResponseDocument), new StreamResult(writer));
        }
        catch( final TransformerException e){
            e.printStackTrace();
        }

        return writer;
    }


}