// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.pt_assistant.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.pt_assistant.utils.RouteUtils;

/**
 * Creates a representation of a route relation in the pt_assistant data model,
 * then maintains a list of PTStops and PTWays of a route.
 *
 * @author darya
 *
 */
public class PTRouteDataManager {

    /* The route relation */
    public Relation relation;

    /* Stores all relation members that are PTStops */
    public List<PTStop> ptStops = new ArrayList<>();

    /* Stores all relation members that are PTWays */
    private final List<PTWay> ptWays = new ArrayList<>();

    /*
     * Stores relation members that could not be created because they are not
     * expected in the model for public_transport version 2
     */
    private final Set<RelationMember> failedMembers = new HashSet<>();

    private final HashMap<String, String> tags = new HashMap<>(30);

    public PTRouteDataManager(Relation relation) {

        // It is assumed that the relation is a route. Build in a check here
        // (e.g. from class RouteUtils) if you want to invoke this constructor
        // from outside the pt_assitant SegmentChecker)

        this.relation = relation;

        PTStop prev = null; // stores the last created PTStop
        for (RelationMember member : this.relation.getMembers()) {
            if (PTStop.isPTStop(member)) {
                // First, check if the stop already exists (i.e. there are
                // consecutive elements that belong to the same stop:
                boolean stopExists = false;
                if (prev != null) {
                    if (
                        Optional.ofNullable(prev.getName()).map(String::isEmpty).orElse(true) ||
                        Optional.ofNullable(member.getMember().get("name")).map(String::isEmpty).orElse(true)
                    ) {
                        // if there is no name, check by proximity:
                        // Squared distance of 0.000004 corresponds to
                        // around 100 m
                        if (member.getMember().getBBox().getCenter().distanceSq(prev.getMember().getBBox().getCenter()) < 0.000001) {
                            stopExists = true;
                        }
                    } else {
                        // if there is a name, check by name comparison:
                        if (prev.getName().equalsIgnoreCase(member.getMember().get("name"))) {
                            stopExists = true;
                        }
                    }
                }

                // check if there are consecutive elements that belong to
                // the same stop:
                if (stopExists) {
                    // this PTStop already exists, so just add a new
                    // element:
                    prev.addStopElement(member);
                    // TODO: something may need to be done if adding the
                    // element
                    // did not succeed. The failure is a result of the same
                    // stop
                    // having >1 stop_position, platform or stop_area.
                } else {
                    // this PTStop does not exist yet, so create it:
                    try {
                        PTStop ptstop = new PTStop(member);
                        ptStops.add(ptstop);
                        prev = ptstop;
                    } catch (IllegalArgumentException ex) {
                        if (ex.getMessage().equals(
                                "The RelationMember type does not match its role " + member.getMember().getName())) {
                            failedMembers.add(member);
                        } else {
                            throw ex;
                        }
                    }

                }

            } else if (RouteUtils.isPTWay(member)) {
                PTWay ptway = new PTWay(member);
                ptWays.add(ptway);
            } else {
                failedMembers.add(member);
            }
        }
    }

    public void set(String key, String value) {
        if (!key.isEmpty() && !value.isEmpty()) {
            this.tags.put(key, value);
        }
    }

    public String get(String key) {
        String value = "";
        if (!key.isEmpty()) {
            if (this.tags.containsKey(key)) {
                value = this.tags.get(key);
            } else {
                value = this.relation.get(key);
            }
        }
        if (value == null || value.isEmpty()) {
            value = "";
        }
        return value;
    }

    public void writeTagsToRelation() {
        Relation tempRel = new Relation(this.relation);
        String v;
        for (String k : this.tags.keySet()) {
            v = this.tags.get(k);
            if (v != null && !v.isEmpty()) {
                tempRel.put(k, v);
            }
        }
        UndoRedoHandler.getInstance().add(new ChangeCommand(this.relation, tempRel));
    }

    public String getComposedName() {
        String composedName = get("operator");
        if (composedName.isEmpty()) {
            composedName = get("network");
        }
        composedName += " " + get("ref");

        if (!get("from").isEmpty()) {
            composedName += " " + get("from");
        }
        if (!get("via").isEmpty()) {
            composedName += " - " + get("via");
        }
        if (!get("to").isEmpty()) {
            composedName += " - " + get("to");
        }

        return composedName;
    }

    public List<PTStop> getPTStops() {
        return ptStops;
    }

    public List<PTWay> getPTWays() {
        return ptWays;
    }

    public int getPTStopCount() {
        return ptStops.size();
    }

    public int getPTWayCount() {
        return ptWays.size();
    }

    public PTStop getFirstStop() {
        if (ptStops.isEmpty()) {
            return null;
        }
        return ptStops.get(0);
    }

    public PTStop getLastStop() {
        if (ptStops.isEmpty()) {
            return null;
        }
        return ptStops.get(ptStops.size() - 1);
    }

    public Set<RelationMember> getFailedMembers() {
        return failedMembers;
    }

    /**
     * Returns the route relation for which this manager was created:
     *
     * @return the route relation for which this manager was created
     */
    public Relation getRelation() {
        return relation;
    }

    /**
     * Returns a PTStop that matches the given id. Returns null if not found
     *
     * @param id identifier
     * @return a PTStop that matches the given id. Returns null if not found
     */
    public PTStop getPTStop(long id) {
        for (PTStop stop : ptStops) {
            if (stop.getStopPosition() != null && stop.getStopPosition().getId() == id) {
                return stop;
            }

            if (stop.getPlatform() != null && stop.getPlatform().getId() == id) {
                return stop;
            }
        }

        return null;
    }

    /**
     * Returns the PTWay which comes directly after the given ptway according to
     * the existing route member sorting
     *
     * @param ptway way
     * @return the PTWay which comes directly after the given ptway according to
     * the existing route member sorting
     */
    public PTWay getNextPTWay(PTWay ptway) {

        for (int i = 0; i < ptWays.size() - 1; i++) {
            if (ptWays.get(i) == ptway) {
                return ptWays.get(i + 1);
            }
        }
        return null;

    }

    /**
     * Returns a sequence of PTWays that are between the start way and the end
     * way. The resulting list includes the start and end PTWays.
     *
     * @param start start way
     * @param end end way
     * @return a sequence of PTWays that are between the start way and the end way
     */
    public List<PTWay> getPTWaysBetween(Way start, Way end) {

        List<Integer> potentialStartIndices = new ArrayList<>();
        List<Integer> potentialEndIndices = new ArrayList<>();

        for (int i = 0; i < ptWays.size(); i++) {
            if (ptWays.get(i).getWays().contains(start)) {
                potentialStartIndices.add(i);
            }
            if (ptWays.get(i).getWays().contains(end)) {
                potentialEndIndices.add(i);
            }
        }

        List<int[]> pairList = new ArrayList<>();
        for (Integer potentialStartIndex : potentialStartIndices) {
            for (Integer potentialEndIndex : potentialEndIndices) {
                if (potentialStartIndex <= potentialEndIndex) {
                    int[] pair = {potentialStartIndex, potentialEndIndex};
                    pairList.add(pair);
                }
            }
        }

        int minDifference = Integer.MAX_VALUE;
        int[] mostSuitablePair = {0, 0};
        for (int[] pair : pairList) {
            int diff = pair[1] - pair[0];
            if (diff < minDifference) {
                minDifference = diff;
                mostSuitablePair = pair;
            }
        }

        List<PTWay> result = new ArrayList<>();
        for (int i = mostSuitablePair[0]; i <= mostSuitablePair[1]; i++) {
            result.add(ptWays.get(i));
        }
        return result;
    }

    /**
     * Returns the common Node of two PTWays or null if there is no common Node.
     * If there is more than one common Node, only the first found is returned.
     *
     * @param way1 first way
     * @param way2 second way
     * @return the common Node of two PTWays or null if there is no common Node
     */
    public Node getCommonNode(PTWay way1, PTWay way2) {

        List<Way> wayList1 = way1.getWays();
        List<Way> wayList2 = way2.getWays();

        HashSet<Node> nodeSet1 = new HashSet<>();
        for (Way w : wayList1) {
            nodeSet1.addAll(w.getNodes());
        }
        HashSet<Node> nodeSet2 = new HashSet<>();
        for (Way w : wayList2) {
            nodeSet2.addAll(w.getNodes());
        }

        for (Node n : nodeSet1) {
            if (nodeSet2.contains(n)) {
                return n;
            }
        }

        return null;
    }

    /**
     * Returns the first way of this route
     *
     * @return the first way of this route
     */
    public Way getFirstWay() {
        if (ptWays.isEmpty()) {
            return null;
        }

        PTWay lastPTWay = ptWays.get(0);
        if (lastPTWay == null || lastPTWay.getWays().isEmpty()) {
            return null;
        }

        return lastPTWay.getWays().get(0);
    }

    /**
     * Returns the last way of this route
     *
     * @return the last way of this route
     */
    public Way getLastWay() {
        if (ptWays.isEmpty()) {
            return null;
        }

        PTWay lastPTWay = ptWays.get(ptWays.size() - 1);
        if (lastPTWay == null || lastPTWay.getWays().isEmpty()) {
            return null;
        }

        return lastPTWay.getWays().get(lastPTWay.getWays().size() - 1);
    }

}
