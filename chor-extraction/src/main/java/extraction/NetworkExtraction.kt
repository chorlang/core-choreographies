package extraction

import ast.cc.interfaces.CCNode
import ast.sp.interfaces.Behaviour
import ast.sp.interfaces.ExtractionLabel
import ast.sp.labels.*
import ast.sp.nodes.*
import ast.cc.interfaces.Choreography
import ast.cc.nodes.*
import org.jgrapht.DirectedGraph
import org.jgrapht.graph.DefaultDirectedGraph
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

typealias network = SortedMap<String, ProcessBehaviour>

class NetworkExtraction {
    private var gmap = HashMap<String, ArrayList<Node>>()

    interface Node {}

    data class ConcreteNode(val nodenet: Network, val str: String, var bad: HashSet<Node>):Node {
        fun copy(): ConcreteNode {
            val temp = nodenet.copy()
            val set = bad.clone()
            return ConcreteNode(temp, str, set as HashSet<Node>)
        }
    }

    data class FakeNode(val procedureName:String):Node

    fun extract(n: Network): Program {
        val graph = DefaultDirectedGraph<Node, ExtractionLabel>(ExtractionLabel::class.java)
        val node = ConcreteNode(n, "0", HashSet())
        graph.addVertex(node)
        addToGlobalMap(node)

        buildGraph(node, graph as DirectedGraph<ConcreteNode,ExtractionLabel>)
        //graph.vertexSet().stream().forEach { i -> println(i.nodenet.network.toString()) }
        //graph.edgeSet().stream().forEach { i -> print(i) }
        val map = unroll(node, graph)
        return buildChoreography(node, map, graph)
    }

    private fun buildChoreography(root: Node, map: TreeMap<String, Node>, graph:DirectedGraph<Node,ExtractionLabel>): Program {
        val main = bh(root, graph)
        val procedures = ArrayList<ProcedureDefinition>()
        for (procedure in map){
            procedures.add(ProcedureDefinition(procedure.key, bh(procedure.value), HashSet<String>()))
        }

        return Program(main as Choreography, procedures)
    }

    private fun bh(node: Node, graph: DirectedGraph<Node, ExtractionLabel>): CCNode {
        val edges = graph.outgoingEdgesOf(node)

        when (edges.size) {
            0 -> {
                when( node ) {
                    is ConcreteNode -> {
                        for (p in node.nodenet.network) {
                            if (!(p.value.main is TerminationSP)) throw Exception("Bad graph. No more edges found, but not all processes were terminated.")
                            else return Termination()
                        }
                    }
                    is FakeNode -> {
                        //... put procedure invocation
                    }
                }

            }
            1 -> {
                val e = edges.first()
                when (e) {

                    is CommunicationLabel -> {
                        return Communication(e.sender, e.receiver, e.expression, bh(graph.getEdgeTarget(e)))
                    }

                    is SelectionLabel -> {
                        return Selection(e.receiver, e.sender, e.label, bh(graph.getEdgeTarget(e)))
                    }

                    is FakeLabel -> {
                        val el = e.label
                        when (el) {
                            is CommunicationLabel -> {
                                return Communication(el.sender, el.receiver, el.expression, ProcedureInvocation(e.procedure, HashSet()))
                            }
                            is SelectionLabel -> {
                                return Selection(el.receiver, el.sender, el.label, ProcedureInvocation(e.procedure, HashSet()))
                            }
                            else -> throw Exception("Unexpected label inside the procedure")
                        }

                        return ProcedureInvocation(e.procedure, HashSet())
                    }
                    else -> throw Exception("Unexpected label type, can't build choreography")
                }
            }
            2 -> {
                val e1 = edges.first()

                if (e1 is ThenLabel) {
                    return Condition(e1.expression, e1.process, bh(graph.getEdgeTarget(e1)), bh(graph.getEdgeTarget(edges.last())))
                } else if (e1 is ElseLabel) {
                    return Condition(e1.expression, e1.process, bh(graph.getEdgeTarget(edges.last())), bh(graph.getEdgeTarget(e1)))
                } else throw Exception("Bad graph. Was waiting for conditional edges, but got unexpected type.")
            }
        }
        return Termination()
    }

    private fun unroll(root: ConcreteNode, graph: DirectedGraph<Node, ExtractionLabel>): TreeMap<String, Node> {
        val map = TreeMap<String, Node>()

        first@ for (node in graph.vertexSet()) {
            if (node is ConcreteNode && graph.incomingEdgesOf(node).size > 1) {

                second@  for (p in node.nodenet.network){
                    if (p.value.main is ProcedureInvocationSP){

                        break@second
                    }
                    else throw Exception("Bad graph. No procedure invocation in a loop node")
                }

            }
        }

        if (graph.incomingEdgesOf(root).size == 1){
            for (p in root.nodenet.network){
                if (p.value.main is ProcedureInvocationSP){
                    map.put((p.value.main as ProcedureInvocationSP).procedure, root)
                    break
                }
                else throw Exception("Bad graph. No procedure invocation in a loop node")
            }
        }

        return map
    }

    private fun buildGraph(nn: ConcreteNode, graph: DirectedGraph<ConcreteNode, ExtractionLabel>): Boolean {
        val node = nn.copy()
        val n = node.nodenet.network

        for (p in n) {
            unfold(p.key, n)

            val findComm = findCommunication(p.key, n)
            if (findComm.sendreceive.isPresent || findComm.selectoffer.isPresent) {
                val (tgt, lbl) = getCommunication(n, findComm)
                if (isAllProceduresVisited(tgt)) {
                    lbl.flipped = true
                    wash(tgt)
                }

                /* case1*/
                val nodeInGraph = graph.vertexSet().stream().filter { i -> i.nodenet.equals(tgt) }.findAny()
                if (!nodeInGraph.isPresent) {
                    val newnode = createNewNode(tgt, lbl, node)
                    addNewNode(nn, newnode, lbl)
                    if (buildGraph(newnode)) return true else continue
                }
                /* case 2 */
                else {
                    checkLoop(node, nodeInGraph.get(), lbl)
                    addNewEdge(nn, nodeInGraph.get(), lbl)
                    return true
                }

            } else if (findCondition(n)) {
                val (tgt1, lbl1, tgt2, lbl2) = getCondition(n)
                if (isAllProceduresVisited(tgt1)) {
                    wash(tgt1)
                    lbl1.flipped = true
                }
                if (isAllProceduresVisited(tgt2)) {
                    wash(tgt2)
                    lbl2.flipped = true
                }

                /* case4 */
                var thenNode: Node
                val tnodeInGraph = graph.vertexSet().stream().filter { n -> n.equals(tgt1) }.findAny()
                if (!tnodeInGraph.isPresent) {
                    thenNode = createNewNode(tgt1, lbl1, node)
                    addNewNode(nn, thenNode, lbl1)
                    if (!buildGraph(thenNode)) continue
                }
                /* case 5 */
                else {
                    thenNode = tnodeInGraph.get()
                    checkLoop(node, thenNode, lbl1)
                    addNewEdge(nn, thenNode, lbl1)
                }

                /* case 7 */
                var elseNode: Node
                val enodeInGraph = graph.vertexSet().stream().filter { n -> n.equals(tgt2) }.findAny()
                if (!enodeInGraph.isPresent) {
                    elseNode = createNewNode(tgt2, lbl2, node)
                    addNewNode(nn, elseNode, lbl2)
                    if (!buildGraph(elseNode)) continue
                }
                /* case 8 */
                else {
                    elseNode = enodeInGraph.get()
                    checkLoop(node, elseNode, lbl2)
                    addNewEdge(nn, elseNode, lbl2)
                }

                relabel(thenNode)
                relabel(elseNode)
                return true
            } else if (allTerminated(n)) {
                return true
            }
        }
        return false
    }

    private fun createNewNode(tgt: Network, lbl: ExtractionLabel, node: NetworkExtraction.ConcreteNode): ConcreteNode {
        var str = node.str
        when (lbl) {
            is ThenLabel -> str = node.str + "0"
            is ElseLabel -> str = node.str + "1"
        }

        if (lbl.flipped) {
            return ConcreteNode(tgt, str, HashSet())
        } else {
            val newnode = ConcreteNode(tgt, str, node.bad.clone() as HashSet<Node>)
            newnode.bad.add(node)
            return newnode
        }
    }

    private fun allTerminated(n: SortedMap<String, ProcessBehaviour>): Boolean {
        for (p in n) {
            if (!(p.value.main is TerminationSP)) return false
        }
        return true
    }

    private fun addNewNode(node: Node, newnode: Node, lbl: ExtractionLabel) {
        graph.addVertex(newnode)
        graph.addEdge(node, newnode, lbl)
        addToGlobalMap(node)
    }

    private fun addNewEdge(node: Node, newnode: Node, lbl: ExtractionLabel) {
        val exstnode = checkPrefix(newnode)
        if (exstnode != null) {
            val l = checkLoop(node, newnode, lbl)
            if (l) {
                //connect with the node
                graph.addEdge(node, exstnode, lbl)

            } else throw Exception("Bad loop!")
        }
    }

    private fun relabel(n: Node) {
        val key = n.str.dropLast(1)
        addToGlobalMap(Node(n.nodenet, key, n.bad))
        removeFromGmap(n)
    }

    private fun removeFromGmap(n: Node) {
        val rn = gmap.get(n.str)
        if (rn != null) {
            val nd = rn.find { i -> i.equals(n) }
            if (nd != null) {
                rn.remove(nd)
            }
        }
    }

    private fun unfold(p: String, n: network): Boolean {
        val pb = n.get(p)
        if (pb?.main is ProcedureInvocationSP) {
            val pi = pb.main as ProcedureInvocationSP
            val pn = pi.procedure
            val pd = pb.procedures.get(pn)
            pb.main = pd?.behaviour?.copy() ?: throw Exception("Can't unfold the process")
            markProcedure(pb.main, true)
            return true
        } else return false
    }

    private fun markProcedure(bh: Behaviour, b: Boolean) {
        when (bh) {
            is ProcedureInvocationSP -> {
                bh.visited = b
            }
            is ConditionSP -> {
                markProcedure(bh.thenBehaviour, b)
                markProcedure(bh.elseBehaviour, b)
            }
            is Offering -> {
                for (l in bh.labels) {
                    markProcedure(l.value, b)
                }
            }
            is Sending -> {
                markProcedure(bh.continuation, b)
            }
            is SelectionSP -> {
                markProcedure(bh.continuation, b)
            }
            is Receiving -> {
                markProcedure(bh.continuation, b)
            }
        }

    }

    private fun checkPrefix(n: Node): Node? {
        for (node in gmap) {
            if (node.key.startsWith(n.str)) return node.value.first()
        }
        return null
    }

    private fun checkLoop(snode: Node, tnode: Node, lbl: ExtractionLabel): Boolean {
        if (lbl.flipped) return true

        if (snode.bad.contains(tnode)) {
            val set = snode.bad.clone() as HashSet<Node>
            set.add(tnode)
            recompute(set, tnode)
            return true
        } else return false

    }

    private fun recompute(set: HashSet<Node>, n: Node) {
        val edges = graph.outgoingEdgesOf(n)
        for (e in edges) {
            if (!e.flipped) {
                val tn = graph.getEdgeTarget(e)
                tn.bad = set.clone() as HashSet<Node>
                recompute(set, tn)
            }
        }
    }

    private fun addToGlobalMap(node: Node) {
        val gnodes = gmap.get(node.str)
        gnodes?.add(node) ?: let {
            val list = ArrayList<Node>()
            list.add(node)
            gmap.put(node.str, list)
        }

    }

    data class GetCommunication(val sendreceive: (Optional<Pair<ProcessBehaviour, ProcessBehaviour>>), val selectoffer: (Optional<Pair<ProcessBehaviour, ProcessBehaviour>>))

    private fun findCommunication(p: String, n: network): GetCommunication {
        var communication: Optional<Pair<ProcessBehaviour, ProcessBehaviour>>
        communication = Optional.empty()

        val pb = n.get(p)

        if (pb?.main is Sending) {
            val receiving = n.values.stream().filter { j -> j.main is Receiving && p == (j.main as Receiving).process }.findFirst()
            if (receiving.isPresent) {
                communication = Optional.of(Pair(pb, receiving.get()))
            }
        } else if (pb?.main is Receiving) {
            val sending = n.values.stream().filter { j -> j.main is Sending && p == (j.main as Sending).process }.findFirst()
            if (sending.isPresent) {
                communication = Optional.of(Pair(sending.get(), pb))
            }
        }

        if (communication.isPresent) {
            return GetCommunication(communication, Optional.empty())
        } else {
            if (pb?.main is SelectionSP) {
                val offering = n.values.stream().filter { j -> j.main is Offering && p == (j.main as Offering).process }.findFirst()
                if (offering.isPresent) {
                    communication = Optional.of(Pair(pb, offering.get()))
                }
            } else if (pb?.main is Offering) {
                val selection = n.values.stream().filter { j -> j.main is SelectionSP && p == (j.main as SelectionSP).process }.findFirst()
                if (selection.isPresent) {
                    communication = Optional.of(Pair(selection.get(), pb))
                }
            }
        }
        return GetCommunication(Optional.empty(), communication)
    }

    private fun getCommunication(n: network, findComm: GetCommunication): Pair<Network, CommunicationLabel> {
        if (findComm.sendreceive.isPresent) {
            val sending = findComm.sendreceive.get().first
            val receiving = findComm.sendreceive.get().second

            val pbl = TreeMap<String, ProcessBehaviour>(n)

            val receivingProcess = (sending.main as Sending).process
            val sendingProcess = (receiving.main as Receiving).process

            pbl.replace(receivingProcess, ProcessBehaviour(receiving.procedures, (receiving.main as Receiving).continuation))
            pbl.replace(sendingProcess, ProcessBehaviour(sending.procedures, (sending.main as Sending).continuation))

            val label = CommunicationLabel(sendingProcess, receivingProcess, (sending.main as Sending).expression)

            return Pair(Network(pbl), label)

        } else if (findComm.selectoffer.isPresent) {
            val selection = findComm.selectoffer.get().first
            val offering = findComm.selectoffer.get().second

            val pbl = TreeMap<String, ProcessBehaviour>(n)

            val selectionProcess = (offering.main as Offering).process
            val offeringProcess = (selection.main as SelectionSP).process

            val offeringBehavior = (offering.main as Offering).labels.get((selection.main as SelectionSP).expression)
                    ?: throw Exception("Trying to select the labal that wasn't offered")

            pbl.replace(offeringProcess, ProcessBehaviour(offering.procedures, offeringBehavior))
            pbl.replace(selectionProcess, ProcessBehaviour(selection.procedures, (selection.main as SelectionSP).continuation))

            val label = CommunicationLabel(offeringProcess, selectionProcess, (selection.main as SelectionSP).expression)

            return Pair(Network(pbl), label)

        } else throw IllegalArgumentException("getCommunication invoked, but no communication found")
    }

    private fun findCondition(n: network): Boolean {
        return !n.values.filter { i -> i.main is ConditionSP }.isEmpty()
    }

    data class ResultCondition(val tmp1: Network, val lb1: ThenLabel, val tmp2: Network, val lbl2: ElseLabel)

    private fun getCondition(n: network): ResultCondition {
        val c = n.entries.stream().filter { i -> i.value.main is ConditionSP }.findFirst().get()
        val process = c.key
        val conditionPB = c.value
        val conditionMain = conditionPB.main as ConditionSP

        val pbelsemap = TreeMap<String, ProcessBehaviour>(n)
        val pbthenmap = TreeMap<String, ProcessBehaviour>(n)

        pbelsemap.replace(process, ProcessBehaviour(conditionPB.procedures, conditionMain.elseBehaviour))
        pbthenmap.replace(process, ProcessBehaviour(conditionPB.procedures, conditionMain.thenBehaviour))

        return ResultCondition(Network(pbelsemap), ThenLabel(process, conditionMain.expression), Network(pbthenmap), ElseLabel(process, conditionMain.expression))
    }

    private fun isAllProceduresVisited(n: Network): Boolean {
        n.network.values.forEach { i ->
            run {
                if (!isProcedureVisited(i.main)) return false
            }
        }
        return true
    }

    private fun isProcedureVisited(b: Behaviour): Boolean {
        when (b) {
            is ProcedureInvocationSP -> {
                if (!b.visited) {
                    return false
                }
            }

            is ConditionSP -> {
                isProcedureVisited(b.elseBehaviour)
                isProcedureVisited(b.thenBehaviour)
            }

            is Sending -> isProcedureVisited(b.continuation)

            is Receiving -> isProcedureVisited(b.continuation)

            is Offering -> b.labels.values.forEach { i -> isProcedureVisited(i) }

            is SelectionSP -> isProcedureVisited(b.continuation)

            is TerminationSP -> return true
        }
        return true
    }

    private fun wash(n: Network) {
        n.network.values.forEach { b -> markProcedure(b.main, false) }
    }
}
