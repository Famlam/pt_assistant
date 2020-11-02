package org.openstreetmap.josm.plugins.pt_assistant.actions;

import static java.util.Collections.emptyList;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.gui.dialogs.relation.IRelationEditor;
import org.openstreetmap.josm.gui.dialogs.relation.MemberTableModel;
import org.openstreetmap.josm.gui.dialogs.relation.RelationEditor;
import org.openstreetmap.josm.gui.dialogs.relation.actions.AbstractRelationEditorAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.IRelationEditorActionAccess;
import org.openstreetmap.josm.gui.dialogs.relation.actions.IRelationEditorUpdateOn;
import org.openstreetmap.josm.plugins.pt_assistant.data.RouteSegmentToExtract;
import org.openstreetmap.josm.plugins.pt_assistant.utils.RouteUtils;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Utils;

/*
Extracts selected members to a new route relation
and substitutes them with this new route relation
in the original relation

 * @author Florian, Polyglot
 */
public class ExtractRelationMembersToNewRelationAction extends AbstractRelationEditorAction {
    public ExtractRelationMembersToNewRelationAction(IRelationEditorActionAccess editorAccess) {
        super(editorAccess, IRelationEditorUpdateOn.MEMBER_TABLE_SELECTION,
            IRelationEditorUpdateOn.MEMBER_TABLE_CHANGE,
            IRelationEditorUpdateOn.TAG_CHANGE);
        new ImageProvider("dialogs/relation", "extract_relation").getResource().attachImageIcon(this);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        final MemberTableModel memberTableModel = editorAccess.getMemberTableModel();
        IRelationEditor editor = editorAccess.getEditor();
        final Relation originalRelation = editor.getRelation();

        final Collection<RelationMember> selectedMembers = memberTableModel.getSelectedMembers();

        List<Command> commands = new ArrayList<>();

        String title = I18n.tr("Extract part from relation?");
        String question = I18n.tr(
            "Do you want to create a new relation with the {0} selected members?",
            selectedMembers.size()
        );
        List<Relation> parentRelations = new ArrayList<>(Utils.filteredCollection(originalRelation.getReferrers(), Relation.class));
        parentRelations.removeIf(parentRelation -> !Objects.equals(parentRelation.get("type"), "superroute"));
        final int numberOfParentRelations = parentRelations.size();
        final JCheckBox cbReplaceInSuperrouteRelations = new JCheckBox(I18n.tr(
            "This route is a member of {0} superroute {1}, add the extracted relation there instead of in this relation?",
            numberOfParentRelations,
            I18n.trn("relation", "relations", numberOfParentRelations)));
        final JCheckBox cbConvertToSuperroute = new JCheckBox(I18n.tr("Convert this relation to superroute"));
        final JCheckBox cbProposed = new JCheckBox(I18n.tr("subrelation is proposed (wenslijn)"));
        final JCheckBox cbDeviation = new JCheckBox(I18n.tr("subrelation is deviation (omleiding)"));
        final JCheckBox cbFindAllSegmentsAutomatically = new JCheckBox(I18n.tr("Process complete route relation for segments and extract into multiple sub route relations"));
        final JTextField tfNameTag = new JTextField(getEditor().getRelation().get("name"));
        ArrayList<Object> paramsList = new ArrayList<>();
        paramsList.add(question);
        paramsList.add(tfNameTag);
        // Only show cbReplaceInSuperrouteRelations when it's relevant
        if (numberOfParentRelations > 0) {
            // check the check box
            cbReplaceInSuperrouteRelations.setSelected(true);
            paramsList.add(cbReplaceInSuperrouteRelations);
        }
        if (originalRelation.hasTag("cycle_network", "cycle_highway")) {
            paramsList.add(cbDeviation);
            paramsList.add(cbProposed);
        }
        paramsList.add(cbConvertToSuperroute);
        if (RouteUtils.isVersionTwoPTRoute(originalRelation)) {
            cbConvertToSuperroute.setSelected(true);
            cbFindAllSegmentsAutomatically.setSelected(true);
            paramsList.add(cbFindAllSegmentsAutomatically);
        }
        paramsList.add(cbConvertToSuperroute);
        Object[] params = paramsList.toArray(new Object[0]); // paramsList.size()]);
        if (
            JOptionPane.showConfirmDialog(
                getMemberTable(),
                params,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
            ) == JOptionPane.OK_OPTION
        ) {
            if (cbFindAllSegmentsAutomatically.isSelected()) {
                splitInSegments(originalRelation, cbConvertToSuperroute.isSelected());
            } else {
                Relation clonedRelation = new Relation(originalRelation);
                RouteSegmentToExtract segment = new RouteSegmentToExtract(clonedRelation,
                    Arrays.stream(memberTableModel.getSelectedIndices()).boxed().collect(Collectors.toList()));
                segment.put("name", tfNameTag.getText());
                if (cbProposed.isSelected()) {
                    segment.put("state", "proposed");
                    segment.put("name", tfNameTag.getText() + " (wenslijn)");
                }
                if (cbDeviation.isSelected()) {
                    segment.put("name", tfNameTag.getText() + " (omleiding)");
                }
                Relation extractedRelation = segment.extractToRelation(
                    Arrays.asList("type", "route", "cycle_network", "network", "operator", "ref"),
                    true,
                    true);
                if (extractedRelation != null) {
                    if (extractedRelation.isNew() && cbConvertToSuperroute.isSelected()) {
                        clonedRelation.put("type", "superroute");
                    }
                    commands.add(new ChangeCommand(originalRelation, clonedRelation));
                    if (cbReplaceInSuperrouteRelations.isSelected()) {
                        addExtractedRelationToParentSuperrouteRelations(
                            originalRelation, commands, parentRelations,
                            segment.getIndices(), extractedRelation);
                    }
                    UndoRedoHandler.getInstance().add(
                        new SequenceCommand(I18n.tr("Extract ways to relation"), commands));

                    RelationEditor extraEditor = RelationEditor.getEditor(
                        getEditor().getLayer(), extractedRelation, emptyList());
                    extraEditor.setVisible(true);
                    extraEditor.setAlwaysOnTop(true);
                }
            }
            /*
            This consistently causes an IndexOutOfBounds exception AND
            I don't know why.
            */
            try {
                editor.reloadDataFromRelation();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void splitInSegments(Relation originalRelation, Boolean convertToSuperroute) {
        ArrayList<RelationMember> segmentRelationsList = new ArrayList<>();
        ArrayList<Integer> indicesToRemoveList = new ArrayList<>();
        final List<RelationMember> members = originalRelation.getMembers();
        RouteSegmentToExtract segment = new RouteSegmentToExtract(originalRelation);
        segment.setActiveDataSet(originalRelation.getDataSet());
        RouteSegmentToExtract newSegment;
        for (int i = 0; i < members.size(); i++) {
            newSegment = segment.addPTWayMember(i);
            if (newSegment != null) {
                Relation extractedRelation = segment.extractToRelation(Arrays.asList("type", "route"),
                    false, false);
                if (extractedRelation != null) {
                    segmentRelationsList.add(new RelationMember("", extractedRelation));
                }
                segment = newSegment;
            }
            if (i < originalRelation.getMembersCount() && RouteUtils.isPTWay(originalRelation.getMembers().get(i))) {
                indicesToRemoveList.add(0, i);
            }
        }
        final Relation clonedRelation = new Relation(originalRelation);
        if (convertToSuperroute) {
            clonedRelation.put("type", "superroute");
        }
        for (Integer integer : indicesToRemoveList) {
            clonedRelation.removeMember(integer);
        }
        segmentRelationsList.forEach(clonedRelation::addMember);
        UndoRedoHandler.getInstance().add(new ChangeCommand(originalRelation, clonedRelation));
    }
    /** This method modifies clonedRelation in place if substituteWaysWithRelation is true
     *  and if ways are extracted into a new relation
     *  It takes a list of (preferably) consecutive indices
     *  If there is already a relation with the same members in the same order
     *  it returns a pointer to that relation
     *  otherwise it returns a new relation with a negative id,
     *  which still needs to be added using addCommand()*/

    private void addExtractedRelationToParentSuperrouteRelations(Relation originalRelation, List<Command> commands, List<Relation> parentRelations, List<Integer> selectedIndices, Relation extractedRelation) {
        for (Relation superroute : parentRelations) {
            int index = 0;
            for (RelationMember member : superroute.getMembers()) {
                if (member.getMember().getId() == originalRelation.getId()) {
                    Relation clonedParentRelation = new Relation(superroute);
                    if (selectedIndices.get(0) != 0) {
                        // if the user selected a block that didn't start with
                        // the first member, add the extracted relation after
                        // the position where this relation was in the parent
                        index++;
                    }
                    index = RouteSegmentToExtract.limitIntegerTo(index, superroute.getMembersCount());
                    clonedParentRelation.addMember(index, new RelationMember("", extractedRelation));
                    commands.add(new ChangeCommand(superroute, clonedParentRelation));
                    break;
                }
                index++;
            }
        }
    }

    @Override
    public boolean isExpertOnly() {
        return true;
    }

    @Override
    protected void updateEnabledState() {
        final boolean newEnabledState = !editorAccess.getSelectionTableModel().getSelection().isEmpty()
            && Optional.ofNullable(editorAccess.getTagModel().get("type")).filter(it -> it.getValue().matches("route|superroute")).isPresent();
            // && !editorAccess.getEditor().isDirtyRelation(); // This seems to cause a problem

        if (newEnabledState) {
            putValue(SHORT_DESCRIPTION, I18n.tr("Extract selected part of the route into new relation"));
        } else {
            putValue(SHORT_DESCRIPTION, I18n.tr("Extract into new relation (needs type=route/superroute tag and at least one selected relation member)"));
        }
        setEnabled(newEnabledState);
    }
}
