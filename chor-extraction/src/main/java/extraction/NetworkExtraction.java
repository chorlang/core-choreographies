package extraction;

import ast.sp.interfaces.ExtractionLabel;
import ast.sp.interfaces.SPNode;
import ast.sp.labels.Communication;
import ast.sp.labels.Then;
import ast.sp.nodes.*;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class NetworkExtraction {
    DirectedGraph<HashMap<String,SPNode>, ExtractionLabel> graph;

    public NetworkExtraction(SPNode sp) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        graph = new DefaultDirectedGraph<>(ExtractionLabel.class);
        HashMap<String, SPNode> network = ((Network) sp).getNetwork();

        graph.addVertex(network);
        Deque<HashMap<String, SPNode>> deque = new ArrayDeque<>();
        deque.add( network );
        extract(deque);
    }

    public DirectedGraph<HashMap<String,SPNode>, ExtractionLabel> getGraph() {
        return graph;
    }

    public void graphToChoreograpy(){

        if (findroot().isPresent()){


        } else {
            //asyclic graph
        }

        for (ExtractionLabel entry : graph.edgeSet()) {
            HashMap<String, SPNode> source =  graph.getEdgeSource(entry);
            HashMap<String, SPNode> target =  graph.getEdgeTarget(entry);

        }

    }

    private Optional<HashMap<String,SPNode>> findroot(){
        StringBuilder builder = new StringBuilder();
        for (HashMap<String, SPNode> entry : graph.vertexSet()) {
            if (graph.inDegreeOf(entry) == 0){
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private void extract(Deque< HashMap<String, SPNode> > networks)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        while( !networks.isEmpty() ) {
            HashMap<String, SPNode> network = networks.removeFirst();

            for (Map.Entry<String, SPNode> process : network.entrySet()) {
                SPNode processBehaviour = process.getValue();

                if (processBehaviour instanceof Receiving) {

                    Receiving receiving = (Receiving) processBehaviour;
                    String sendingProcess = receiving.getProcess();

                    SPNode node = network.get(sendingProcess);
                    if ((node != null) && (node instanceof Sending) && (((Sending) node).getProcess().equals(process.getKey()))) {
                        Sending sending = (Sending) node;
                        String receivingProcess = sending.getProcess();

                        ExtractionLabel label = new Communication(sendingProcess, receivingProcess, sending.getExpression());
                        HashMap<String, SPNode> nextNode = new HashMap<>(network);

                        nextNode.put(receivingProcess, receiving.getContinuation());
                        nextNode.put(sendingProcess, sending.getContinuation());

                        graph.addVertex(nextNode);
                        graph.addEdge(network, nextNode, label);

                        networks.addLast(nextNode);
                    }
                } else if (processBehaviour instanceof Sending) {

                    Sending sending = (Sending) processBehaviour;
                    String receivingProcess = sending.getProcess();

                    SPNode node = network.get(receivingProcess);
                    if ((node != null) && (node instanceof Receiving) && (((Receiving) node).getProcess().equals(process.getKey()))) {
                        Receiving receiving = (Receiving) node;
                        String sendingProcess = receiving.getProcess();

                        ExtractionLabel label = new Communication(sendingProcess, receivingProcess, sending.getExpression());
                        HashMap<String, SPNode> nextNode = (HashMap<String, SPNode>) network.clone();

                        nextNode.put(receivingProcess, receiving.getContinuation());
                        nextNode.put(sendingProcess, sending.getContinuation());

                        graph.addVertex(nextNode);
                        graph.addEdge(network, nextNode, label);

                        networks.addLast(nextNode);
                    }
                } else if (processBehaviour instanceof Offering) {

                    Offering offering = (Offering) processBehaviour;
                    String selectionProcess = offering.getProcess();

                    SPNode node = network.get(offering.getProcess());
                    if ((node != null) && (node instanceof Selection) && (((Selection) node).getProcess().equals(process.getKey()))) {
                        Selection selection = (Selection) node;
                        String offeringProcess = selection.getProcess();

                        ExtractionLabel label = new ast.sp.labels.Selection(selectionProcess, offeringProcess, selection.getLabel());

                        HashMap<String, SPNode> nextNode = (HashMap<String, SPNode>) network.clone();
                        nextNode.put(selectionProcess, selection.getContinuation());
                        nextNode.put(offeringProcess, offering.getLabels().get(selection.getLabel()));

                        graph.addVertex(nextNode);
                        graph.addEdge(network, nextNode, label);

                        networks.addLast(nextNode);
                    }
                } else if (processBehaviour instanceof Selection) {

                    Selection selection = (Selection) processBehaviour;
                    String offeringProcess = selection.getProcess();

                    SPNode node = network.get(selection.getProcess());
                    if ((node != null) && (node instanceof Offering) && (((Offering) node).getProcess().equals(process.getKey()))) {
                        Offering offering = (Offering) node;
                        String selectionProcess = offering.getProcess();

                        ExtractionLabel label = new ast.sp.labels.Selection(selectionProcess, offeringProcess, selection.getLabel());
                        HashMap<String, SPNode> nextNode = (HashMap<String, SPNode>) network.clone();
                        nextNode.put(selectionProcess, selection.getContinuation());
                        nextNode.put(offeringProcess, offering.getLabels().get(selection.getLabel()));

                        graph.addVertex(nextNode);
                        graph.addEdge(network, nextNode, label);

                        networks.addLast(nextNode);
                    }
                } else if (processBehaviour instanceof Condition) {
                    Condition condition = (Condition) processBehaviour;

                    ExtractionLabel label = new Then(condition.getProcess(), condition.getExpression());

                    SPNode thennode = condition.getThenBehaviour();
                    HashMap<String, SPNode> networkThen = (HashMap<String, SPNode>) network.clone();
                    networkThen.put(process.getKey(), thennode);
                    graph.addVertex(networkThen);
                    graph.addEdge(network, networkThen, label);
                    networks.addLast(networkThen);

                    SPNode elsenode = condition.getElseBehaviour();
                    HashMap<String, SPNode> networkElse = (HashMap<String, SPNode>) network.clone();
                    networkElse.put(process.getKey(), elsenode);
                    graph.addVertex(networkElse);
                    graph.addEdge(network, networkElse, label);

                    networks.addLast(networkElse);
                }
            }
        }
    }
}