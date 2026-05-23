package com.example.michiru;

/**
 * Reusable loading-overlay manager.
 *
 * Binds to a dim layer, a loading-card wrapper, and a message label from FXML.
 * Optionally blurs a content pane while the overlay is visible.
 */

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class OverlayManager {

    private static final Interpolator SILK = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);

    private final StackPane overlayDim;
    private final StackPane loadingWrapper;
    private final Label messageLabel;
    /** Optional — the main content node to blur while loading. */
    private Node blurTarget;
    /** Tracks the current blur effect so hide() can animate it back. */
    private GaussianBlur activeBlur;

    /**
     * Binds the overlay to the dim layer, card wrapper, and message label supplied from FXML.
     */
    public OverlayManager(StackPane overlayDim,
                          StackPane loadingWrapper,
                          Label messageLabel) {
        this.overlayDim = overlayDim;
        this.loadingWrapper = loadingWrapper;
        this.messageLabel = messageLabel;
    }

    /**
     * Sets the node that should be blurred while the overlay is visible.
     * Pass {@code null} to disable the blur effect.
     */
    public void setBlurTarget(Node blurTarget) {
        this.blurTarget = blurTarget;
    }

    /**
     * Shows the overlay on the JavaFX thread and sets the status text beneath the spinner.
     * If a blur target has been set, it is blurred with a smooth animation.
     */
    public void show(String message) {
        if (messageLabel != null) {
            messageLabel.setText(message);
        }
        overlayDim.setVisible(true);
        loadingWrapper.setVisible(true);

        // Animate opacity of the dim + wrapper
        overlayDim.setOpacity(0);
        loadingWrapper.setOpacity(0);

        Timeline fadeIn;

        if (blurTarget != null) {
            activeBlur = new GaussianBlur(0);
            blurTarget.setEffect(activeBlur);
            fadeIn = new Timeline(
                    new KeyFrame(Duration.millis(220),
                            new KeyValue(overlayDim.opacityProperty(), 1.0, SILK),
                            new KeyValue(loadingWrapper.opacityProperty(), 1.0, SILK),
                            new KeyValue(activeBlur.radiusProperty(), 12.0, SILK))
            );
        } else {
            fadeIn = new Timeline(
                    new KeyFrame(Duration.millis(220),
                            new KeyValue(overlayDim.opacityProperty(), 1.0, SILK),
                            new KeyValue(loadingWrapper.opacityProperty(), 1.0, SILK))
            );
        }

        fadeIn.play();
    }

    /**
     * Hides the overlay on the JavaFX thread after background work completes.
     * Blur is removed with a smooth reverse animation.
     */
    public void hide() {
        Timeline fadeOut;

        if (activeBlur != null) {
            fadeOut = new Timeline(
                    new KeyFrame(Duration.millis(180),
                            new KeyValue(overlayDim.opacityProperty(), 0.0, SILK),
                            new KeyValue(loadingWrapper.opacityProperty(), 0.0, SILK),
                            new KeyValue(activeBlur.radiusProperty(), 0.0, SILK))
            );
        } else {
            fadeOut = new Timeline(
                    new KeyFrame(Duration.millis(180),
                            new KeyValue(overlayDim.opacityProperty(), 0.0, SILK),
                            new KeyValue(loadingWrapper.opacityProperty(), 0.0, SILK))
            );
        }

        fadeOut.setOnFinished(e -> {
            overlayDim.setVisible(false);
            loadingWrapper.setVisible(false);
            if (blurTarget != null) {
                blurTarget.setEffect(null);
            }
            activeBlur = null;
        });

        fadeOut.play();
    }

    /**
     * Returns whether the dim layer is currently visible.
     */
    public boolean isVisible() {
        return overlayDim.isVisible();
    }
}
