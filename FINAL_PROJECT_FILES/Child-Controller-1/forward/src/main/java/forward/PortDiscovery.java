
/* COMSE-6998 SDN Project Fall 2014
   Team: Avinash Sridhar (as4626), Nachiket Rau (nnr2107), Shruti Ramesh (sr3155), Sareena Abdul Razak (sta2378)
   Port Discovery Child Controller-1
 
   RecA Module to communicate link status with the
   Parent :  192.168.56.1 : 6633
   Child 1 : 192.168.56.20 : 6633
   Child 2 : 192.168.56.21 : 6633
   Run MssM.java on Floodlight VM with 192.168.56.101 -> Use this for TCP connection
 Change name to PortDiscovery.java
 */
package forward;

import reca.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.lang.String;
import java.util.Map;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.Runtime;
import java.lang.Process;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.opendaylight.controller.sal.core.ConstructionException;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.apache.commons.codec.binary.Base64;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;

public class PortDiscovery {

    public static JSONObject getTopology(String user, String password, String baseURL) {

        StringBuffer result = new StringBuffer();
        try {

            // Create URL = base URL 
            URL url = new URL(baseURL);

            // Create authentication string and encode it to Base64
            String authStr = user + ":" + password;
            String encodedAuthStr = Base64.encodeBase64String(authStr
                .getBytes());

            // Create Http connection
            HttpURLConnection connection = (HttpURLConnection) url
                .openConnection();

            // Set connection properties
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Basic " + encodedAuthStr);
            connection.setRequestProperty("Accept", "application/json");

            // Get the response from connection's inputStream
            InputStream content = (InputStream) connection.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(content));
            String line = "";
            while ((line = in.readLine()) != null) {
                result.append(line);
            }

            JSONObject topology = new JSONObject(result.toString());
            System.out.println("PortDiscovery::getTopology(): Returned topology to getPort()");
            return topology;
        } 
        catch (Exception e) {
        e.printStackTrace();
        }

    return null;
    }

    // This is to test the REST API pull from parent controller, along with JSON parsing
    public Set<NodeConnector> getPortSet() throws JSONException {

        boolean check = false;
        // Call the getTopology function with username password and URL, modify the IP if needed
        JSONObject topology = getTopology("admin","admin","http://192.168.56.21:8081/controller/nb/v2/topology/default");
        System.out.println("PortDiscovery::getPort(): Obtained JSON Topology from getTopology()");

        String[] stringSet = new String[4];

        if (topology != null) {
            
            // Convert the obtained JSONObject into JSONArray format
            JSONArray topologyArray = topology.getJSONArray("egdeProperties");
            int lengthTopoArray = topologyArray.length();

            if (topologyArray != null) {
                for (int i = 0; i < lengthTopoArray; i = i + 1) {
                    try {
                        JSONObject obj = topologyArray.getJSONObject(i);
                        JSONObject edge = obj.getJSONObject("edge");
                        JSONObject edgeTail = edge.getJSONObject("tailNodeConnector");
                        JSONObject edgeTailNode = edgeTail.getJSONObject("node");
                        JSONObject edgeHead = edge.getJSONObject("headNodeConnector");
                        JSONObject edgeHeadNode = edgeHead.getJSONObject("node");
                        String edgeHeadNodeString = edgeHeadNode.get("id").toString();
                        String edgeTailNodeString = edgeTailNode.get("id").toString();
                        stringSet[(2*i)] = edgeTailNodeString;
                        stringSet[(2*i) + 1] = edgeHeadNodeString;
                    }
                    catch(JSONException je) {System.out.println("PortDiscovery::getTopology(): Caught JSON Exception"); }
                }

                // Now check if the every set of tail and head node connectors have a reverse associated with it within the stringSet array
                for (int i = 0; i < lengthTopoArray; i = i + 1) {
                    check = false;
                    String[] tailHead = new String[2];
                    tailHead[0] = stringSet[(2*i)];
                    tailHead[1] = stringSet[(2*i) + 1];
                    for (int j = 0; j < lengthTopoArray; j = j + 1) {
                        if (tailHead[0].equals(stringSet[(2*i) + 1]) && tailHead[1].equals(stringSet[(2*i)])) {
                            check = check | true;
                            break;
                        }
                        else { check = check | false; }
                    }
                    if (!check) {
                        return null;
                    }
                }
                if (check) {
                    // Unhash setNodeConn declaration too and its return value
                    //Set<NodeConnector> setNodeConn;
                    RecA recA = new RecA();
                    // Unhash below later 
                    Set<NodeConnector> setNodeConn= recA.getTail();
                    return setNodeConn;
                }
            }
        }
        // We should hopefully never come till here, but we need to return something in case none of the above happens
        return null;
    } 
}
