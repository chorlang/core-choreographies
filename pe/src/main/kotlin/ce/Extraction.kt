package ce

import ast.cc.interfaces.CCNode
import ast.sp.nodes.interfaces.IBehaviour
import ast.sp.labels.interfaces.ExtractionLabel
import ast.sp.labels.*
import ast.sp.nodes.*
import ast.cc.interfaces.Choreography
import ast.cc.interfaces.Interaction
import ast.cc.nodes.*
import ast.sp.nodes.interfaces.ActionSP
import ast.cc.nodes.Communication
import ast.sp.labels.interfaces.InteractionLabel
import org.apache.logging.log4j.LogManager
import org.jgrapht.DirectedGraph
import org.jgrapht.graph.DefaultDirectedGraph
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

typealias ProcessMap = HashMap<String, ProcessTerm>
typealias GraphNode = Pair<Network,InteractionLabel>

class NetworkExtraction {
    private val log = LogManager.getLogger()
    private var nodeIdCounter = 0

    //region Main
    /**
     * entry point for choreography extraction algorithm:
     * 1. build graph with nodes as networks and edges as choreography actions
     * 2. remove cycles from the graph
     * 3. traverse the graph reading choreography actions
     * @param n a processes from which a choreography will be extracted
     * @return Program representation of resulted choreography
     */
    private fun extract(n: Network, strategy: Strategy): Program {
        val graph = DefaultDirectedGraph<Node, ExtractionLabel>(ExtractionLabel::class.java)
        val node = ConcreteNode(n, "0", nextNodeId(), ArrayList())
        graph.addVertex(node)
        addToChoicePathMap(node)

        buildGraph(node, graph as DirectedGraph<ConcreteNode, ExtractionLabel>, strategy)

        val fklist = unrollGraph(node, graph as DirectedGraph<Node, ExtractionLabel>)
        return buildChoreography(node, fklist, graph)
    }

    private fun nextNodeId():Int {
        return nodeIdCounter++
    }

    companion object {
        fun run(n:Network, s:Strategy):Program {
            return NetworkExtraction().extract(n, Strategy.SelectFirst)
        }
    }

    private fun buildGraph(currentNode: ConcreteNode, graph: DirectedGraph<ConcreteNode, ExtractionLabel>, strategy: Strategy): Boolean {
        //val node = currentNode.copy()
        val unfolded = HashSet<String>() //Storing unfolded procedures
        //val processes = sortProcesses(currentNode, strategy) //Sorting processes by the strategy passed from the outside
        val processes = currentNode.network.copy().processes

        //region Try to find a single-action communication
        for (processPair in processes) {

            //if the processPair has procedure invocation on top, try to unfold it
            if (unfold(processPair.key, processes[processPair.key]!!)) unfolded.add(processPair.key)

            val findComm = findCommunication(processPair.key, processes)
            if (findComm != null) {
                val (targetNode, label) = getCommunication(processes, findComm)

                //remove processes that were unfolded but don't participate in the current communication
                unfolded.remove(label.rcv)
                unfolded.remove(label.snd)

                unfolded.forEach { targetNode.processes[it]?.main = currentNode.network.processes[it]?.main?.copy()!! }

                //if all procedures were visited, flip all markings
                if (isAllProceduresVisited(targetNode.processes)) {
                    label.flipped = true
                    wash(targetNode)
                }

                /* case1*/
                val nodeInGraph = graph.vertexSet().stream().filter { i -> i.network.equals(targetNode) && (currentNode.choicePath).startsWith(i.choicePath) }.findAny()
                if (!nodeInGraph.isPresent) {
                    val newnode = createNewNode(targetNode, label, currentNode)
                    addNewNode(currentNode, newnode, label, graph)
                    log.debug(label)
                    return if (buildGraph(newnode, graph, strategy)) true else continue
                }
                /* case 2 */
                else if (processes.values.none { pr -> isfinite(pr.main) && !isterminated(pr) }) {

                    if (addNewEdge(currentNode, nodeInGraph.get(), label, graph)) return true
                    else {
                        cleanNode(findComm, processes, currentNode)
                        unfolded.forEach{u -> unfold(u, processes[u]!!)}
                    }
                } else {
                    cleanNode(findComm, processes, currentNode)
                    unfolded.forEach{u -> unfold(u, processes[u]!!)}
                }

            } else if (findCondition(processes)) {
                val (tgt1, lbl1, tgt2, lbl2) = getCondition(processes)

                unfolded.remove(lbl1.process)

                unfolded.forEach { tgt1.processes[it]?.main = currentNode.network.processes[it]?.main?.copy()!! }
                unfolded.forEach { tgt2.processes[it]?.main = currentNode.network.processes[it]?.main?.copy()!! }

                if (isAllProceduresVisited(tgt1.processes)) {
                    wash(tgt1)
                    lbl1.flipped = true
                }
                if (isAllProceduresVisited(tgt2.processes)) {
                    wash(tgt2)
                    lbl2.flipped = true
                }

                /* case4 */
                var thenNode: Node
                val tnodeInGraph = graph.vertexSet().stream().filter { i -> i.network.equals(tgt1) && (currentNode.choicePath + "0").startsWith(i.choicePath) }.findAny()
                if (!tnodeInGraph.isPresent) {
                    thenNode = createNewNode(tgt1, lbl1, currentNode)
                    addNewNode(currentNode, thenNode, lbl1, graph)
                    log.debug(lbl1)
                    if (!buildGraph(thenNode, graph, strategy)) continue
                }
                /* case 5 */
                else {
                    thenNode = tnodeInGraph.get()
                    addNewEdge(currentNode, thenNode, lbl1, graph)
                }

                /* case 7 */
                var elseNode: Node
                val enodeInGraph = graph.vertexSet().stream().filter { i -> i.network.equals(tgt2) && (currentNode.choicePath + "1").startsWith(i.choicePath) }.findAny()
                if (!enodeInGraph.isPresent) {
                    elseNode = createNewNode(tgt2, lbl2, currentNode)
                    addNewNode(currentNode, elseNode, lbl2, graph)
                    log.debug(lbl2)
                    if (!buildGraph(elseNode, graph, strategy)) continue
                }
                /* case 8 */
                else {
                    elseNode = enodeInGraph.get()
                    addNewEdge(currentNode, elseNode, lbl2, graph)
                }

                relabel(thenNode)
                relabel(elseNode)
                return true
            } else if (allTerminated(processes)) {
                return true
            }
        }
        //endregion
        //region Try to find a multi-action communication

        for (p in processes) {

            //val tmp_node = node.copy()
            //val tmp_node_net = tmp_node.processes.processes

            val actions = ArrayList<InteractionLabel>()
            val waiting = ArrayList<InteractionLabel>()
            val b = createInteractionLabel(p.key, processes)
            if (b != null) waiting.add(b)


            val receivers = ArrayList<String>()
            while (!waiting.isEmpty()) {
                val lbl = waiting.first()
                waiting.remove(lbl)
                //multicom can't contain actions with the same receiver
                if (receivers.contains(lbl.rcv)) throw MulticomException("multicom can't contain actions with the same receiver")
                actions.add(lbl)
                receivers.add(lbl.rcv)

                val receiver = lbl.rcv
                val sender = lbl.snd

                val rcv_pb = processes[receiver] //val rcv_pb = tmp_node_net[receiverTerm]

                //fill the list of waiting actions with sending/selection actions from the receiverTerm process behaviour if it is in correct format
                //return the new receiverTerm pb if success and null otherwise. waiting list is populated implicitly
                val new_rcv_b = fillWaiting(waiting, actions, lbl, rcv_pb!!.main, processes[receiver]!!.procedures)
                if (new_rcv_b!=null) {
                    val snd_pb = processes[sender] //val snd_pb = tmp_node_net[senderTerm]
                    val snd_pb_main = snd_pb!!.main

                    processes.replace(receiver, ProcessTerm(rcv_pb.procedures, new_rcv_b)) // tmp_node_net.replace(receiverTerm, ProcessTerm(rcv_pb.procedures, new_rcv_b))
                    val snd_cont = if (snd_pb_main is SendingSP) snd_pb_main.continuation else if (snd_pb_main is SelectionSP) snd_pb_main.continuation else throw UnsupportedOperationException()

                    processes.replace(sender, ProcessTerm(snd_pb.procedures, snd_cont)) // tmp_node_net.replace(senderTerm, ProcessTerm(snd_pb.procedures, snd_cont))
                }
            }

            //if we managed to collect some actions for a multicom
            if (actions.size >= 2) {
                //create label
                val lbl = MulticomLabel(actions)

                //fold back unfolded procedures that were not participating in communication
                lbl.labels.forEach { l ->
                    unfolded.remove(l.rcv)
                    unfolded.remove(l.snd)
                }
                unfolded.forEach { processes[it]?.main = currentNode.network.processes[it]?.main?.copy()!! } // unfolded.forEach { tmp_node_net[it]?.main = currentNode.processes.processes[it]?.main?.copy()!! }

                //if all procedures were visited, flip all markings
                if (isAllProceduresVisited(processes)) {
                    lbl.flipped = true
                    wash(Network(processes))
                }

                //add new edge or node to the graph
                val nodeInGraph = graph.vertexSet().stream().filter { i -> i.network.equals(Network(processes)) && (currentNode.choicePath).startsWith(i.choicePath) }.findAny()
                if (!nodeInGraph.isPresent) {
                    val newnode = createNewNode(Network(processes), lbl, currentNode)
                    addNewNode(currentNode, newnode, lbl, graph)
                    log.debug(lbl)
                    return if (buildGraph(newnode, graph, strategy)) true else continue
                }
                else {
                    if (processes.values.filter { pr -> isfinite(pr.main) && !isterminated(pr) }.isEmpty()) {

                        if (addNewEdge(currentNode, nodeInGraph.get(), lbl, graph)) return true
                        /*else {
                            cleanNode(findComm, processes, currentNode)
                            TODO ("replace with tmp_node")
                        }*/
                    } /*else {
                                cleanNode(findComm, processes, currentNode)
                                TODO ("replace with tmp_node")
                            }*/
                }
            }

        }
        //endregion
        //region Throw exception, if there is no possible actions
        throw ProcessStarvationException("Process starvation at node" + currentNode.toString())
        //endregion
    }

    private fun fillWaiting(waiting: ArrayList<InteractionLabel>, actions: ArrayList<InteractionLabel>, lbl: InteractionLabel, rcv_pb_main: IBehaviour, rcv_proc: HashMap<String, IBehaviour>): IBehaviour?
    {
        when (rcv_pb_main){
            is SendingSP -> {
                //look into continuation to be sure that it is in the form a1;...;ak;r?;B where each ai is either the sending of a value or a label selection
                val snd_cont = fillWaiting(waiting, actions, lbl, rcv_pb_main.continuation, rcv_proc)

                if (snd_cont!=null){
                    val new_lbl = SendingLabel(lbl.rcv, rcv_pb_main.receiver, rcv_pb_main.expression)
                    if (!actions.contains(new_lbl)) waiting.add(new_lbl)
                    return SendingSP(snd_cont, new_lbl.rcv, new_lbl.expr)
                } else {
                    return null
                }
            }
            is SelectionSP -> {
                //look into continuation to be sure that it is in the form a1;...;ak;r?;B where each ai is either the sending of a value or a label selection
                val sel_cont = fillWaiting(waiting, actions, lbl, rcv_pb_main.continuation, rcv_proc)

                if (sel_cont!=null){
                    val new_lbl = SelectionLabel(lbl.rcv, rcv_pb_main.receiver, rcv_pb_main.expression)
                    if (!actions.contains(new_lbl)) waiting.add(new_lbl)
                    return SelectionSP(sel_cont, lbl.rcv, lbl.expr)
                }
            }
            is ReceiveSP -> if (lbl.snd == rcv_pb_main.sender && lbl is SendingLabel) return rcv_pb_main.continuation

            is OfferingSP -> if (lbl.snd == rcv_pb_main.sender && lbl is SelectionLabel) return rcv_pb_main.labels[lbl.label]!!

            is ProcedureInvocationSP -> {
                val new_pb = ProcessTerm(rcv_proc, rcv_pb_main)
                if (!rcv_pb_main.visited) unfold(lbl.rcv, new_pb)
                return fillWaiting(waiting, actions, lbl, new_pb.main, rcv_proc)
            }
        }
        return null
    }

    private fun unrollGraph(root: ConcreteNode, graph: DirectedGraph<Node, ExtractionLabel>): ArrayList<FakeNode> {
        val fklist = ArrayList<FakeNode>()

        var c = 1
        val rnodes = HashMap<ConcreteNode, String>()


        graph.vertexSet().forEach { node -> if (node is ConcreteNode && graph.incomingEdgesOf(node).size > 1) rnodes.put(node, c++.toString()) }

        rnodes.forEach { node ->
            node.run {
                val fk = FakeNode("X" + node.value, node.key)
                graph.addVertex(fk)
                fklist.add(fk)
                val labels = graph.outgoingEdgesOf(node.key)

                val targets = ArrayList<LabelTarget>()
                labels.forEach { label -> targets.add(LabelTarget(label, graph.getEdgeTarget(label))) }

                graph.removeAllEdges(ArrayList<ExtractionLabel>(graph.outgoingEdgesOf(node.key)))
                targets.forEach { s -> graph.addEdge(fk, s.target, s.lbl) }
            }
        }


        if (graph.incomingEdgesOf(root).size == 1) {
            val fk = FakeNode(generateProcedureName(), root)
            graph.addVertex(fk)
            fklist.add(fk)
            val label = graph.outgoingEdgesOf(root).first()
            val target = graph.getEdgeTarget(label)
            graph.removeEdge(label)
            graph.addEdge(fk, target, label)

        }

        return fklist
    }

    private fun buildChoreography(root: Node, fklist: ArrayList<FakeNode>, graph: DirectedGraph<Node, ExtractionLabel>): Program {
        val main = bh(root, graph, fklist)
        val procedures = ArrayList<ProcedureDefinition>()
        for (fk in fklist) {
            procedures.add(ProcedureDefinition(fk.procedureName, bh(fk, graph, fklist), HashSet()))
        }

        return Program(main as Choreography, procedures)
    }

    private fun bh(node: Node, graph: DirectedGraph<Node, ExtractionLabel>, fklist: ArrayList<FakeNode>): CCNode {
        val edges = graph.outgoingEdgesOf(node)

        when (edges.size) {
            0 -> {
                if (node is ConcreteNode) {
                    for (fk in fklist) {
                        if (fk.source.equals(node)) return ProcedureInvocation(fk.procedureName, HashSet())
                    }

                    for (p in node.network.processes) {
                        if (p.value.main !is TerminationSP)
                            throw Exception("Bad graph. No more edges found, but not all processes were terminated.")
                        else return Termination()
                    }

                }

            }
            1 -> {
                val e = edges.first()
                return when (e) {

                    is SendingLabel -> {
                        val com = Communication(e.sender, e.receiver, e.expression)
                        CommunicationSelection(com, bh(graph.getEdgeTarget(e), graph, fklist))
                    }

                    is SelectionLabel -> {
                        val sel = Selection(e.receiver, e.sender, e.label)
                        CommunicationSelection(sel, bh(graph.getEdgeTarget(e), graph, fklist))
                    }

                    is MulticomLabel -> {
                        val act = ArrayList<Interaction>()
                        for (l in e.labels) {
                            when (l) {
                                is SelectionLabel -> {
                                    act.add(Selection(l.snd, l.rcv, l.label))
                                }
                                is SendingLabel -> {
                                    act.add(Communication(l.snd, l.rcv, l.expr))
                                }
                                else -> throw NotImplementedError()
                            }
                        }
                        Multicom(act, bh(graph.getEdgeTarget(e), graph, fklist))
                    }

                    else -> throw Exception("Unexpected label type, can't build choreography")
                }
            }
            2 -> {
                val ef = edges.first()
                val el = edges.last()

                return when (ef) {
                    is ThenLabel -> Condition(ef.process, ef.expression, bh(graph.getEdgeTarget(ef), graph, fklist), bh(graph.getEdgeTarget(el), graph, fklist))
                    is ElseLabel -> Condition(ef.process, ef.expression, bh(graph.getEdgeTarget(el), graph, fklist), bh(graph.getEdgeTarget(ef), graph, fklist))
                    else -> throw Exception("Bad graph. Was waiting for conditional edges, but got unexpected type.")
                }
            }
        }
        return Termination()
    }
    //endregion
    //region Unfold
    private fun unfold(p: String, pb: ProcessTerm): Boolean {
        val pb_main = pb.main

        return if (pb_main is ProcedureInvocationSP) {

            val pr = pb_main.procedure
            val pr_def = pb.procedures[pr]

            pb.main = pr_def?.copy() ?: throw Exception("Can't unfold the process") //TODO("meaningful exception for unfold")
            markProcedure(pb.main, true)

            if (pr_def is ProcedureInvocationSP) {
                unfold(p, pb)
            }
            true
        } else false
    }
    private fun wash(n: Network) {
        n.processes.values.forEach { b -> unmarkProcedure(b.main) }
    }
    //endregion
    //region Procedures marking and checking
    private fun markProcedure(bh: IBehaviour, b: Boolean) {
        when (bh) {
            is ProcedureInvocationSP -> {
                bh.visited = b
            }
            is ConditionSP -> {
                markProcedure(bh.thenBehaviour, b)
                markProcedure(bh.elseBehaviour, b)
            }
            is OfferingSP -> {
                for (l in bh.labels) {
                    markProcedure(l.value, b)
                }
            }
            is SendingSP -> {
                markProcedure(bh.continuation, b)
            }
            is SelectionSP -> {
                markProcedure(bh.continuation, b)
            }
            is ReceiveSP -> {
                markProcedure(bh.continuation, b)
            }
        }

    }
    private fun unmarkProcedure(bh: IBehaviour) {
        when (bh) {
            is ProcedureInvocationSP -> {
                bh.visited = false
            }
            is ConditionSP -> {
                unmarkProcedure(bh.thenBehaviour)
                unmarkProcedure(bh.elseBehaviour)
            }
            is OfferingSP -> {
                for (l in bh.labels) {
                    unmarkProcedure(l.value)
                }
            }
            is SendingSP -> {
                unmarkProcedure(bh.continuation)
            }
            is SelectionSP -> {
                unmarkProcedure(bh.continuation)
            }
            is ReceiveSP -> {
                unmarkProcedure(bh.continuation)
            }
        }

    }
    private fun isAllProceduresVisited(n: ProcessMap): Boolean {
        return n.values.all{isProcedureVisited(it.main)}
    }
    private fun isProcedureVisited(bh: IBehaviour): Boolean {
        return when (bh) {
            is SendingSP -> isProcedureVisited(bh.continuation)
            is ReceiveSP -> isProcedureVisited(bh.continuation)
            is SelectionSP -> isProcedureVisited(bh.continuation)
            is OfferingSP -> bh.labels.all{isProcedureVisited(it.component2())}
            is ConditionSP -> isProcedureVisited(bh.thenBehaviour) && isProcedureVisited(bh.elseBehaviour)
            is ProcedureInvocationSP -> bh.visited
            is TerminationSP -> true
            else -> false
        }
    }
    //endregion
    //region Termination checks
    private fun isfinite(pr: IBehaviour): Boolean {
        when (pr) {
            is ProcedureInvocationSP -> return false
            is TerminationSP -> return true
            is SendingSP -> return isfinite(pr.continuation)
            is ReceiveSP -> return isfinite(pr.continuation)
            is SelectionSP -> return isfinite(pr.continuation)
            is OfferingSP -> {
                pr.labels.forEach { label -> if (!isfinite(label.value)) return false }
                return true
            }
            is ConditionSP -> {
                return isfinite(pr.elseBehaviour) && isfinite(pr.thenBehaviour)
            }
        }
        return false
    }
    private fun allTerminated(n: HashMap<String, ProcessTerm>): Boolean {
        for (p in n) {
            if (!isterminated(p.value)) return false
        }
        return true
    }
    private fun isterminated(p: ProcessTerm): Boolean {
        return p.main is TerminationSP
    }
    //endregion
    //region Creating interaction and condition nodes
    private fun findCommunication(processName: String, processMap: ProcessMap): GetCommunication? {
        val processTerm = processMap[processName]

        val mainBehaviour = processTerm?.main
        when (mainBehaviour) {
            is SendingSP -> {
                val receive = processMap[mainBehaviour.process]
                if (receive != null && receive.main is ReceiveSP && (receive.main as ReceiveSP).sender == processName) {
                    return SendReceive(processTerm, receive)
                }
            }

            is ReceiveSP -> {
                val send = processMap[(processTerm.main as ReceiveSP).process]
                if (send != null && send.main is SendingSP && (send.main as SendingSP).receiver == processName) {
                    return SendReceive(send, processTerm)
                }
            }

            is SelectionSP -> {
                val offer = processMap[mainBehaviour.process]
                if (offer != null && offer.main is OfferingSP && (offer.main as OfferingSP).sender == processName) {
                    return SelectOffer(processTerm, offer)
                }
            }

            is OfferingSP -> {
                val select = processMap[mainBehaviour.process]
                if (select != null && select.main is SelectionSP && (select.main as SelectionSP).receiver == processName) {
                    return SelectOffer(select, processTerm)
                }
            }

        }
        return null
    }

    private fun getCommunication(processes: ProcessMap, findComm: GetCommunication): GraphNode {
        when (findComm) {
            is SendReceive -> {
                val newProcesses = ProcessMap()
                processes.forEach { key, value -> newProcesses.put(key, value.copy()) }

                val senderTerm = findComm.senderTerm
                val receiverTerm = findComm.receiverTerm

                val receiverName = (senderTerm.main as SendingSP).receiver
                val senderName = (receiverTerm.main as ReceiveSP).sender

                newProcesses.replace(receiverName, ProcessTerm(receiverTerm.procedures, (receiverTerm.main as ReceiveSP).continuation))
                newProcesses.replace(senderName, ProcessTerm(senderTerm.procedures, (senderTerm.main as SendingSP).continuation))

                val label = SendingLabel(senderName, receiverName, (senderTerm.main as SendingSP).expression)

                return GraphNode(Network(newProcesses), label)
            }

            is SelectOffer -> {
                val newProcesses = HashMap<String, ProcessTerm>(processes)

                val selection = findComm.senderTerm
                val offering = findComm.receiverTerm

                val selectionProcess = (offering.main as OfferingSP).process
                val offeringProcess = (selection.main as SelectionSP).process

                val offeringBehavior = (offering.main as OfferingSP).labels[(selection.main as SelectionSP).expression]
                        ?: throw Exception("Trying to senderTerm the label that wasn't offered")

                newProcesses.replace(offeringProcess, ProcessTerm(offering.procedures, offeringBehavior))
                newProcesses.replace(selectionProcess, ProcessTerm(selection.procedures, (selection.main as SelectionSP).continuation))

                val label = SelectionLabel(offeringProcess, selectionProcess, (selection.main as SelectionSP).expression)

                return GraphNode(Network(newProcesses), label)
            }

            else -> throw Exception("FindComm object doesn't belong to SendReceive or SelectOffer types")
        }
    }

    private fun findCondition(n: ProcessMap): Boolean {
        return !n.values.none { i -> i.main is ConditionSP }
    }

    data class ResultCondition(val tmp1: Network, val lb1: ThenLabel, val tmp2: Network, val lbl2: ElseLabel)

    private fun getCondition(n: ProcessMap): ResultCondition {
        val c = n.entries.stream().filter { i -> i.value.main is ConditionSP }.findFirst().get()
        val process = c.key
        val conditionPB = c.value
        val conditionMain = conditionPB.main as ConditionSP

        val pbelsemap = HashMap<String, ProcessTerm>(n)
        val pbthenmap = HashMap<String, ProcessTerm>(n)

        pbelsemap.replace(process, ProcessTerm(conditionPB.procedures, conditionMain.elseBehaviour))
        pbthenmap.replace(process, ProcessTerm(conditionPB.procedures, conditionMain.thenBehaviour))

        return ResultCondition(Network(pbthenmap), ThenLabel(process, conditionMain.expression), Network(pbelsemap), ElseLabel(process, conditionMain.expression))
    }
    //endregion
    //region Manipulations with nodes
    private fun createNewNode(tgt: Network, lbl: ExtractionLabel, node: ConcreteNode): ConcreteNode {
        var str = node.choicePath
        when (lbl) {
            is ThenLabel -> str = node.choicePath + "0"
            is ElseLabel -> str = node.choicePath + "1"
        }

        return if (lbl.flipped) {
            ConcreteNode(tgt, "" + str, nextNodeId(), ArrayList())
        } else {

            //node.bad.badset.mapTo(newb) { it.copy() }
            val newnode = ConcreteNode(tgt, "" + str, nextNodeId(), node.bad.clone() as ArrayList<Int>)
            newnode.bad.add(node.id)
            newnode
        }
    }

    private fun addNewNode(node: ConcreteNode, newnode: ConcreteNode, lbl: ExtractionLabel, graph: DirectedGraph<ConcreteNode, ExtractionLabel>) {
        graph.addVertex(newnode)
        graph.addEdge(node, newnode, lbl)
        addToChoicePathMap(node)
    }

    private fun addNewEdge(nn: ConcreteNode, newnode: ConcreteNode, lbl: ExtractionLabel, graph: DirectedGraph<ConcreteNode, ExtractionLabel>): Boolean {
        val exstnode = checkPrefix(newnode)
        if (exstnode != null) {
            val l = checkLoop(nn, newnode, lbl, graph)
            if (l) {
                graph.addEdge(nn, newnode, lbl)
                return true

            } //else throw BadLoopException("Bad loop!")
        }
        return false
    }

    private fun cleanNode(findComm: GetCommunication, n: ProcessMap, nn: ConcreteNode) {
        val first = if (findComm is SendReceive) findComm.senderTerm else (findComm as SelectOffer).senderTerm
        val second = if (findComm is SendReceive) findComm.receiverTerm else (findComm as SelectOffer).receiverTerm

        val prk1 = (first.main as ActionSP).process
        val prb1new = nn.network.processes.get(prk1)
        val prb1 = n[prk1]
        val prk2 = (second.main as ActionSP).process
        val prb2new = nn.network.processes.get(prk2)
        val prb2 = n[prk2]

        prb1?.main = prb1new?.main?.copy()!!
        prb2?.main = prb2new?.main?.copy()!!
    }
    //endregion
    //region Checkloop and choicePaths manipulations
    private fun checkLoop(source_node: ConcreteNode, target_node: ConcreteNode, lbl: ExtractionLabel, graph: DirectedGraph<ConcreteNode, ExtractionLabel>): Boolean {
        if (lbl.flipped) return true

        if (target_node.equals(source_node)) return false

        // if (!target_node.bad.badset.contains(source_node)) {
        if (!source_node.bad.contains(target_node.id)) {
            val nodeset = HashSet<ConcreteNode>()
            nodeset.addAll(nodeset)
            nodeset.add(source_node)

            val tomark = HashSet<ConcreteNode>()
            tomark.addAll(recompute(target_node, graph, tomark))

            return true
        } else return false

    }
    private fun recompute(n: ConcreteNode, graph: DirectedGraph<ConcreteNode, ExtractionLabel>, tomark: HashSet<ConcreteNode>): HashSet<ConcreteNode> {
        val edges = graph.outgoingEdgesOf(n)
        for (e in edges) {
            if (!e.flipped) {
                val tn = graph.getEdgeTarget(e)
                tomark.add(tn)
                tomark.addAll(recompute(tn, graph, tomark))
            }
        }
        return tomark
    }
    private fun relabel(n: ConcreteNode) {
        val key = n.choicePath.dropLast(1)
        addToChoicePathMap(ConcreteNode(n.network, key, n.id, n.bad))
        removeFromChoicePathMap(n)
    }
    private fun checkPrefix(n: ConcreteNode): ConcreteNode? {
        for (node in choicePaths) {
            if (node.key.startsWith(n.choicePath))
                return if (!node.value.isEmpty()) node.value.first() else null
        }
        return null
    }

    private var choicePaths = HashMap<String, ArrayList<ConcreteNode>>() //global map of processes used in bad loop calculations
    private fun removeFromChoicePathMap(n: ConcreteNode) {
        val rn = choicePaths[n.choicePath]
        if (rn != null) {
            val nd = rn.find { i -> i == n }
            if (nd != null) {
                rn.remove(nd)
            }
        }
    }
    private fun addToChoicePathMap(node: ConcreteNode) {
        val choicePath = choicePaths[node.choicePath]
        if (choicePath!=null){
            choicePath.add(node)
        } else {
            val nodesList = ArrayList<ConcreteNode>()
            nodesList.add(node)
            choicePaths.put(node.choicePath, nodesList)
        }

    }
    //endregion
    //region Exceptions
    class ProcessStarvationException(override val message: String) : Exception(message)
    class MulticomException(override val message: String) : Exception(message)
    //endregion
    //region Data classes and interfaces
    data class LabelTarget(val lbl: ExtractionLabel, val target: Node)
    interface GetCommunication
    data class SendReceive(val senderTerm: ProcessTerm, val receiverTerm: ProcessTerm) : GetCommunication
    data class SelectOffer(val senderTerm: ProcessTerm, val receiverTerm: ProcessTerm) : GetCommunication
    interface Node
    data class ConcreteNode(val network: Network, val choicePath: String, val id: Int, val bad: ArrayList<Int>) : Node {
        fun copy(): ConcreteNode {
            val newnet = network.copy()
            val newb = ArrayList<Int>()

            bad.forEach { newb.add(it) }

            return ConcreteNode(newnet, "" + choicePath, id, newb)
        }

        fun equals(other: ConcreteNode): Boolean{
            return network.equals(other.network) && choicePath == other.choicePath && bad.equals(other.bad)
        }

        override fun toString(): String = ""
    }
    /*data class Bad(val badset: HashSet<ConcreteNode>){
        fun copy(): Bad {
            val newb = HashSet<ConcreteNode>()
            for (b in badset){
                newb.add(b.copy())
            }
            return Bad(newb)
        }

        fun equals(other: Bad): Boolean {
            if (badset.size != other.badset.size) return false
            return true
        }

        fun contains(node: ConcreteNode): Boolean {
            for (b in badset){
                if (b.equals(node)) return true
            }
            return false
        }
    }*/
//    data class GraphNode(val target: Network, val label: ExtractionLabel)
    data class FakeNode(val procedureName: String, val source: Node) : Node
    //endregion
    //region Utils
    @Volatile
    private var count = 0
    private fun generateProcedureName(): String {
        return "X" + count.inc()
    }
    private fun sortProcesses(node: ConcreteNode, strategy: Strategy): HashMap<String, ProcessTerm> {
        val net = node.network.processes
        val copynet = LinkedHashMap<String, ProcessTerm>()

        //put Selection/OfferingSP on top
        net.forEach { pr -> if (pr.value.main is SelectionSP || pr.value.main is OfferingSP) copynet.put("" + pr.key, pr.value.copy()) }
        net.forEach { pr -> copynet.put("" + pr.key, pr.value.copy()) }

        return copynet
    }
    private fun createInteractionLabel(p: String, nodesorted: HashMap<String, ProcessTerm>): InteractionLabel? {
        val pmain = nodesorted.get(p)?.main
        when (pmain) {
            is SendingSP -> {
                return SendingLabel(p, pmain.receiver, pmain.expression)
            }
            is SelectionSP -> {
                return SelectionLabel(p, pmain.receiver, pmain.expression)
            }
            else -> return null
        }
    }
    //endregion
}