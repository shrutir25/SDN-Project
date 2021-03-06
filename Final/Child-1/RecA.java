/*RecA Module to communicate link status with the
 Parent :  192.168.56.1 : 6633
 Child 1 : 192.168.56.20 : 6633
 Child 2 : 192.168.56.21 : 6633
 Run IncomingCmd.java on Floodlight VM with 192.168.56.101 -> Use this for TCP connection
 Change name to RecA.java
 */

package reca;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.*;
import java.io.*;
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
import java.util.concurrent.ConcurrentMap;
import java.lang.Runtime;
import java.lang.Process;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.opendaylight.controller.sal.core.ConstructionException;
import org.opendaylight.controller.sal.core.Host;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.core.Path;
import org.opendaylight.controller.sal.core.Property;
import org.opendaylight.controller.sal.core.Edge;
import org.opendaylight.controller.sal.core.Host.*;
import org.opendaylight.controller.sal.core.UpdateType;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.packet.ARP;
import org.opendaylight.controller.sal.packet.BitBufferHelper;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.Packet;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.Output;
import org.opendaylight.controller.sal.action.Flood;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.topology.TopoEdgeUpdate;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.sal.utils.NetUtils;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.switchmanager.Switch;
import org.opendaylight.controller.switchmanager.Subnet;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.opendaylight.controller.topologymanager.ITopologyManagerAware;
import org.opendaylight.controller.topologymanager.TopologyUserLinkConfig;

public class RecA implements IRouting, ITopologyManager {
    private static final Logger logger = LoggerFactory
    .getLogger(RecA.class);
    private ISwitchManager switchManager = null;
    private ITopologyManager topologyManager = null;
    
    
    //Initialize variables
    private Map<Node, Set<Edge>> edgesForEachNode = new HashMap<Node, Set<Edge>>();
    private Set<Node> allNodes;
    private Set<Edge> NodeEdges;
    private Edge edgeCheck, edgeReverse, testEdge;
    private Map<Node, Map<NodeConnector,NodeConnector>> destNodeMap= new HashMap<Node, Map<NodeConnector,NodeConnector>>();
    private Set<NodeConnector> setOfNodeconnectors = new HashSet<NodeConnector>();
    private String MasterIP = "192.168.56.101"; // IP where we run IncomingCmd.java to handle master linkup/linkdown
    private int MasterPort = 41101; // Use 41102 / 41101 for 2 different child controllers
    private Map<NodeConnector,NodeConnector> extraLink = new HashMap<NodeConnector,NodeConnector>();
    private Map<Node, Edge> extraEdge = new HashMap<Node,Edge>();
    private NodeConnector Head, Tail;
    private DataOutputStream out;
    private String function = "switch";
    private long Timer = 0L, pollTime = 60000L;
    private boolean edgeExists=false;
    
    void setSwitchManager(ISwitchManager s) {
        logger.debug("SwitchManager set");
        this.switchManager = s;
    }
    
    void unsetSwitchManager(ISwitchManager s) {
        if (this.switchManager == s) {
            logger.debug("SwitchManager removed!");
            this.switchManager = null;
        }
    }
    
    void setTopologyManager(ITopologyManager s) {
        logger.debug("TopologyManager set");
        this.topologyManager = s;
    }
    
    void unsetTopologyManager(ITopologyManager s) {
        if (this.topologyManager == s) {
            logger.debug("TopologyManager removed!");
            this.topologyManager = null;
        }
    }
    
    void init() {
        logger.info("Initialized");
        // Disabling the SimpleForwarding and ARPHandler bundle to not conflict with this one
        BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        for(Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getSymbolicName().contains("simpleforwarding")) {
                try {
                    bundle.uninstall();
                } catch (BundleException e) {
                    logger.error("Exception in Bundle uninstall "+bundle.getSymbolicName(), e);
                }
            }
        }
        
    }
    
    /**
     * Function called by the dependency manager when at least one
     * dependency become unsatisfied or when the component is shutting
     * down because for example bundle is being stopped.
     *
     */
    void destroy() {
    }
    
    /**
     * Function called by dependency manager after "init ()" is called
     * and after the services provided by the class are registered in
     * the service registry
     *
     */
    void start() {
        logger.info("Started");
        System.out.println("Reca::start() :- \nRec-A started!");
        RecAImplementation();
        
    }
    
    public void RecAImplementation ()
    {
        Timer = System.currentTimeMillis();
        while (true)
        {
            if (System.currentTimeMillis() - Timer > pollTime)
            {
                System.out.println("Reca::RecaImplementation() :- \nChecking topology for extra edges");
                Timer = System.currentTimeMillis();
                edgesForEachNode = topologyManager.getNodeEdges();
                allNodes = edgesForEachNode.keySet();
                for(Node checkNode : allNodes) {
                    NodeEdges = edgesForEachNode.get(checkNode);
                    for(Edge edgeCheck : NodeEdges)
                    {
                        Head=edgeCheck.getHeadNodeConnector();
                        Tail=edgeCheck.getTailNodeConnector();
                        try {
                            edgeReverse=new Edge(Head,Tail);
                            if(!NodeEdges.contains(edgeReverse))
                            {
                                if(!destNodeMap.containsKey(checkNode))
                                {
                                    System.out.println("Reca::RecaImplementation() :- \nReverse edge Not present, Add to map : ");
                                    //If reverse edge not present, add original edge to map
                                    extraLink.put(Head,Tail);
                                    if(!setOfNodeconnectors.contains(Tail))
                                    {
                                        setOfNodeconnectors.add(Tail);
                                    }
                                    destNodeMap.put(checkNode,extraLink);
                                    extraEdge.put(checkNode,edgeCheck);
                                    sendToMaster(1);  // Send LinkUp to Master
                                }
                            }
                        }
                        catch(ConstructionException c) {
                            System.out.println("Reca::RecaImplementation() :- \nException to create edge!");
                        }
                    }
                    
                    //check if edge is still connected
                    if(extraEdge.containsKey(checkNode))
                    {
                        NodeEdges = edgesForEachNode.get(checkNode);
                        System.out.println("\nNodeEdges are : " + NodeEdges + "\nextraEdge is :" + extraEdge);
                        edgeCheck = extraEdge.get(checkNode);
                        System.out.println("\nReca::RecaImplementation : - ExtraEdge present in map");
                        //Entry present in map but no longer present in topology -> then remove
                        if(!NodeEdges.contains(edgeCheck)) {
                            System.out.println("Reca::RecaImplementation() :- No longer in topology - Delete extra edge!");
                            Tail=edgeCheck.getTailNodeConnector();
                            extraEdge.remove(checkNode);
                            destNodeMap.remove(checkNode);
                            if(setOfNodeconnectors.contains(Tail)) {
                                setOfNodeconnectors.remove(Tail);
                            }
                            sendToMaster(2); // send linkdown to master
                        }
                    }
                }
            }
        }
    }
    
    public Set getTail() {
        if(!setOfNodeconnectors.isEmpty()) {
            return setOfNodeconnectors;
        }
        return null;
    }
    
    public void sendToMaster(int type) {        // Type 1 : Pull , type 2 Delete
        try {
            System.out.println("RecA::RecAImplementation() Connecting to socket  of master");
            Socket client = new Socket(MasterIP, MasterPort);
            OutputStream outToServer = client.getOutputStream();
            out = new DataOutputStream(outToServer);
            String toSend;
            if(type==1) toSend = "Pull-gs1";    //change to gs1 for other child
            else toSend = "Delete-gs1";         // change to gs1 for other child
            out.writeUTF(toSend);
            System.out.println("\nReca::sendToMaster :- \nSent to Master Controller : " + toSend);
        }
        catch (IOException e) {e.printStackTrace();}
    }
    
    /**
     * Function called by the dependency manager before the services
     * exported by the component are unregistered, this will be
     * followed by a "destroy ()" calls
     *
     */
    void stop() {
        logger.info("Stopped");
    }
    
    public Status addUserLink(TopologyUserLinkConfig arg0) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public Status deleteUserLink(String arg0) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public Map<Edge, Set<Property>> getEdges() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public Host getHostAttachedToNodeConnector(NodeConnector arg0) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public List<Host> getHostsAttachedToNodeConnector(NodeConnector arg0) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public Set<NodeConnector> getNodeConnectorWithHost() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public Map<Node, Set<Edge>> getNodeEdges() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public Map<Node, Set<NodeConnector>> getNodesWithNodeConnectorHost() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public ConcurrentMap<String, TopologyUserLinkConfig> getUserLinks() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public boolean isInternal(NodeConnector arg0) {
        // TODO Auto-generated method stub
        return false;
    }
    
    public Status saveConfig() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public void updateHostLink1(NodeConnector arg0, Host arg1, UpdateType arg2,
                                Set<Property> arg3) {
        // TODO Auto-generated method stub
        
    }
    
    public void clear() {
        // TODO Auto-generated method stub
        
    }
    
    public void clearMaxThroughput() {
        // TODO Auto-generated method stub
        
    }
    
    public Path getMaxThroughputRoute(Node arg0, Node arg1) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public Path getRoute(Node arg0, Node arg1) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public Path getRoute(Node arg0, Node arg1, Short arg2) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public void initMaxThroughput(Map<Edge, Number> arg0) {
        // TODO Auto-generated method stub
        
    }
    
    public void updateHostLink(NodeConnector arg0, Host arg1, UpdateType arg2,
                               Set<Property> arg3) {
        // TODO Auto-generated method stub
        
    }
    
}
