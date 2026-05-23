package com.example.michiru;

import com.example.michiru.facade.MentorshipLifecycleFacade;
import com.example.michiru.model.MentorProfile;
import com.example.michiru.model.Skill;
import com.example.michiru.session.UserSession;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;


public class MentorProfileEditViewController implements Initializable {

    private static final Interpolator SILK = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);

    private final MentorshipLifecycleFacade facade = new MentorshipLifecycleFacade();

    @FXML private TextArea  areaBio;
    @FXML private TextField fieldYearsExp;
    @FXML private TextField fieldCreditCost;
    @FXML private CheckBox  chkAvailable;
    @FXML private VBox      skillsContainer;
    @FXML private Label     lblStatus;
    @FXML private Button    btnSave;
    @FXML private Button    btnCancel;
    @FXML private Button    btnClose;

    @FXML private VBox bioSectionContent;
    @FXML private VBox experienceSectionContent;
    @FXML private VBox availabilitySectionContent;
    @FXML private VBox skillsSectionContent;

    @FXML private FontIcon bioChevron;
    @FXML private FontIcon experienceChevron;
    @FXML private FontIcon availabilityChevron;
    @FXML private FontIcon skillsChevron;

    private int mentorUserId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        var user = UserSession.getInstance().getCurrentUser();
        if (user == null) return;
        mentorUserId = user.getUserId();

        loadProfile();
        loadSkills();
    }

    private void loadProfile() {
        MentorProfile profile = facade.getMentorOwnProfile(mentorUserId);
        if (profile == null) {
            showStatus("Could not load profile data.", true);
            return;
        }

        areaBio.setText(profile.getBio() != null ? profile.getBio() : "");
        fieldYearsExp.setText(String.valueOf(profile.getYearsOfExperience()));
        fieldCreditCost.setText(String.valueOf(profile.getCreditCost()));
        chkAvailable.setSelected(profile.isAvailable());
    }

    private void loadSkills() {
        List<Skill> allSkills = facade.getAllSkills();
        Set<Integer> mySkillIds = new HashSet<>(facade.getMentorExpertiseSkillIds(mentorUserId));

        skillsContainer.getChildren().clear();
        for (Skill skill : allSkills) {
            CheckBox cb = new CheckBox(skill.getName());
            cb.setUserData(skill.getSkillId());
            cb.setSelected(mySkillIds.contains(skill.getSkillId()));
            cb.getStyleClass().add("edit-skill-checkbox");
            VBox.setMargin(cb, new Insets(0, 0, 2, 0));
            skillsContainer.getChildren().add(cb);
        }

        if (skillsContainer.getChildren().isEmpty()) {
            Label empty = new Label("No active skills in the catalogue yet.");
            empty.getStyleClass().add("edit-modal-subtitle");
            skillsContainer.getChildren().add(empty);
        }
    }

    @FXML
    private void toggleBioSection() {
        toggleSection(bioSectionContent, bioChevron);
    }

    @FXML
    private void toggleExperienceSection() {
        toggleSection(experienceSectionContent, experienceChevron);
    }

    @FXML
    private void toggleAvailabilitySection() {
        toggleSection(availabilitySectionContent, availabilityChevron);
    }

    @FXML
    private void toggleSkillsSection() {
        toggleSection(skillsSectionContent, skillsChevron);
    }

    private void toggleSection(Node content, FontIcon chevron) {
        boolean expanding = !content.isManaged();

        if (expanding) {
            content.setManaged(true);
            content.setVisible(true);
            content.setOpacity(0.0);
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(content.opacityProperty(), expanding ? 0.0 : 1.0, SILK),
                        new KeyValue(chevron.rotateProperty(), expanding ? -90.0 : 0.0, SILK)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(content.opacityProperty(), expanding ? 1.0 : 0.0, SILK),
                        new KeyValue(chevron.rotateProperty(), expanding ? 0.0 : -90.0, SILK))
        );

        if (!expanding) {
            timeline.setOnFinished(e -> {
                content.setManaged(false);
                content.setVisible(false);
            });
        }

        timeline.play();
    }

    @FXML
    private void handleSave() {
        lblStatus.setText("");
        lblStatus.getStyleClass().remove("edit-status-label-error");

        int yearsExp;
        try {
            yearsExp = Integer.parseInt(fieldYearsExp.getText().strip());
        } catch (NumberFormatException e) {
            showStatus("Years of experience must be a number.", true);
            return;
        }

        int creditCost;
        try {
            creditCost = Integer.parseInt(fieldCreditCost.getText().strip());
        } catch (NumberFormatException e) {
            showStatus("Credit cost must be a number.", true);
            return;
        }

        String bio = areaBio.getText().strip();
        boolean available = chkAvailable.isSelected();

        List<Integer> selectedSkillIds = new ArrayList<>();
        for (var node : skillsContainer.getChildren()) {
            if (node instanceof CheckBox cb && cb.isSelected() && cb.getUserData() instanceof Integer id) {
                selectedSkillIds.add(id);
            }
        }

        btnSave.setDisable(true);
        showStatus("Saving...", false);

        MentorshipLifecycleFacade.OperationResult result = facade.saveMentorProfile(
                mentorUserId, bio, yearsExp, available, creditCost, selectedSkillIds);

        btnSave.setDisable(false);

        if (result.success()) {
            showStatus(result.message(), false);
            closeAfterDelay();
        } else {
            showStatus(result.message(), true);
        }
    }

    @FXML
    private void handleClose() {
        getStage().close();
    }

    private void showStatus(String message, boolean isError) {
        lblStatus.setText(message);
        if (isError) {
            if (!lblStatus.getStyleClass().contains("edit-status-label-error")) {
                lblStatus.getStyleClass().add("edit-status-label-error");
            }
        } else {
            lblStatus.getStyleClass().remove("edit-status-label-error");
        }
    }

    private void closeAfterDelay() {
        PauseTransition pause = new PauseTransition(Duration.millis(800));
        pause.setOnFinished(e -> getStage().close());
        pause.play();
    }

    private Stage getStage() {
        return (Stage) btnSave.getScene().getWindow();
    }
}
