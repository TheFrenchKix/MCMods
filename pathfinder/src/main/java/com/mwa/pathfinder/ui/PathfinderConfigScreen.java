package com.mwa.pathfinder.ui;

import com.mwa.pathfinder.config.PathfinderClientConfig;
import com.mwa.pathfinder.control.PathfinderController;
import com.mwa.pathfinder.pathing.IPathManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class PathfinderConfigScreen extends Screen {
    private final Screen parent;
    private final PathfinderController controller;
    private final IPathManager pathManager;
    private final PathfinderClientConfig config;
    private final Runnable onConfigChanged;

    private TextFieldWidget timeoutField;
    private TextFieldWidget randomLookingField;
    private int selectedTab;

    public PathfinderConfigScreen(Screen parent, PathfinderController controller, IPathManager pathManager, PathfinderClientConfig config, Runnable onConfigChanged) {
        super(Text.literal("Pathfinder Settings"));
        this.parent = parent;
        this.controller = controller;
        this.pathManager = pathManager;
        this.config = config;
        this.onConfigChanged = onConfigChanged;
        this.selectedTab = Math.max(0, Math.min(3, config.selectedTab));
    }

    @Override
    protected void init() {
        clearChildren();

        int panelLeft = width / 2 - 178;
        int panelTop = 22;
        int rowY = panelTop + 6;

        addTabButtons(panelLeft, rowY);
        rowY += 28;

        if (!pathManager.isAvailable()) {
            int x = width / 2 - 75;
            int y = height / 2 - 10;
            addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(x, y + 24, 150, 20)
                .build());
            return;
        }

        int left = panelLeft + 12;
        int right = left + 170;
        int y = rowY;

        if (selectedTab == 0) {
            buildControlPage(left, right, y);
        } else if (selectedTab == 1) {
            buildMovementPage(left, right, y);
        } else if (selectedTab == 2) {
            buildSafetyPage(left, right, y);
        } else {
            buildRenderPage(left, right, y);
        }
    }

    private void addTabButtons(int x, int y) {
        addDrawableChild(tabButton(x, y, 82, "Control", 0));
        addDrawableChild(tabButton(x + 86, y, 82, "Movement", 1));
        addDrawableChild(tabButton(x + 172, y, 82, "Safety", 2));
        addDrawableChild(tabButton(x + 258, y, 82, "Render", 3));
    }

    private ButtonWidget tabButton(int x, int y, int width, String label, int tabIndex) {
        boolean selected = selectedTab == tabIndex;
        String prefix = selected ? "[" : "";
        String suffix = selected ? "]" : "";
        return ButtonWidget.builder(Text.literal(prefix + label + suffix), button -> {
                selectedTab = tabIndex;
                config.selectedTab = tabIndex;
                saveConfig();
                init();
            })
            .dimensions(x, y, width, 20)
            .build();
    }

    private void buildControlPage(int left, int right, int y) {
        addDrawableChild(toggleButton(left, y, buttonLabel("Camera Sync", controller.isCameraSync()), button -> {
            controller.toggleCameraSync();
            config.cameraSync = controller.isCameraSync();
            saveConfig();
            init();
        }));
        addDrawableChild(toggleButton(right, y, buttonLabel("Ignore Y", controller.isIgnoreY()), button -> {
            controller.toggleIgnoreY();
            config.ignoreY = controller.isIgnoreY();
            saveConfig();
            init();
        }));
        y += 24;

        addDrawableChild(toggleButton(left, y, Text.literal("Path To Look Target"), button -> controller.pathToLookTarget()));
        addDrawableChild(toggleButton(right, y, Text.literal("Path To Look Entity"), button -> controller.pathToLookEntity()));
        y += 24;

        addDrawableChild(toggleButton(left, y, Text.literal("Follow Look Entity"), button -> controller.followLookEntity()));
        addDrawableChild(toggleButton(right, y, Text.literal("Path Forward"), button -> controller.pathForward()));
        y += 24;

        addDrawableChild(toggleButton(left, y, Text.literal("Stop"), button -> controller.stop()));
        addDrawableChild(toggleButton(right, y, Text.literal("Save Baritone Settings"), button -> pathManager.saveSettings()));
        y += 32;

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
            .dimensions(right, y, 160, 20)
            .build());
    }

    private void buildMovementPage(int left, int right, int y) {
        addDrawableChild(toggleButton(left, y, buttonLabel("Allow Sprint", pathManager.isAllowSprint()), button -> {
            pathManager.toggleAllowSprint();
            saveConfig();
            init();
        }));
        addDrawableChild(toggleButton(right, y, buttonLabel("Allow Parkour", pathManager.isAllowParkour()), button -> {
            pathManager.toggleAllowParkour();
            saveConfig();
            init();
        }));
        y += 24;

        addDrawableChild(toggleButton(left, y, buttonLabel("Free Look", pathManager.isFreeLook()), button -> {
            pathManager.toggleFreeLook();
            saveConfig();
            init();
        }));
        addDrawableChild(toggleButton(right, y, Text.literal("Random Looking +0.1"), button -> {
            pathManager.setRandomLooking(pathManager.getRandomLooking() + 0.1d);
            saveConfig();
            init();
        }));
        y += 24;

        timeoutField = new TextFieldWidget(textRenderer, left, y, 160, 20, Text.literal("Primary Timeout"));
        timeoutField.setText(String.valueOf(pathManager.getPrimaryTimeoutMs()));
        addDrawableChild(timeoutField);

        randomLookingField = new TextFieldWidget(textRenderer, right, y, 160, 20, Text.literal("Random Looking"));
        randomLookingField.setText(String.valueOf(pathManager.getRandomLooking()));
        addDrawableChild(randomLookingField);
        y += 24;

        addDrawableChild(toggleButton(left, y, Text.literal("Apply Numeric Settings"), button -> applyNumericSettings()));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
            .dimensions(right, y, 160, 20)
            .build());
    }

    private void buildSafetyPage(int left, int right, int y) {
        addDrawableChild(toggleButton(left, y, buttonLabel("Allow Break", pathManager.isAllowBreak()), button -> {
            pathManager.toggleAllowBreak();
            saveConfig();
            init();
        }));
        addDrawableChild(toggleButton(right, y, buttonLabel("Allow Place", pathManager.isAllowPlace()), button -> {
            pathManager.toggleAllowPlace();
            saveConfig();
            init();
        }));
        y += 24;

        addDrawableChild(toggleButton(left, y, buttonLabel("Anti Cheat", pathManager.isAntiCheatCompatibility()), button -> {
            pathManager.toggleAntiCheatCompatibility();
            saveConfig();
            init();
        }));
        addDrawableChild(toggleButton(right, y, buttonLabel("Ignore Y", controller.isIgnoreY()), button -> {
            controller.toggleIgnoreY();
            config.ignoreY = controller.isIgnoreY();
            saveConfig();
            init();
        }));
        y += 32;

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
            .dimensions(right, y, 160, 20)
            .build());
    }

    private void buildRenderPage(int left, int right, int y) {
        addDrawableChild(toggleButton(left, y, buttonLabel("Render Path", pathManager.isRenderPath()), button -> {
            pathManager.toggleRenderPath();
            saveConfig();
            init();
        }));
        addDrawableChild(toggleButton(right, y, buttonLabel("Render Goal", pathManager.isRenderGoal()), button -> {
            pathManager.toggleRenderGoal();
            saveConfig();
            init();
        }));
        y += 24;

        addDrawableChild(toggleButton(left, y, buttonLabel("Overlay Enabled", config.overlayEnabled), button -> {
            config.overlayEnabled = !config.overlayEnabled;
            saveConfig();
            init();
        }));
        addDrawableChild(toggleButton(right, y, buttonLabel("Show Block Target", config.overlayBlockTarget), button -> {
            config.overlayBlockTarget = !config.overlayBlockTarget;
            saveConfig();
            init();
        }));
        y += 24;

        addDrawableChild(toggleButton(left, y, buttonLabel("Show Follow Entity", config.overlayFollowEntity), button -> {
            config.overlayFollowEntity = !config.overlayFollowEntity;
            saveConfig();
            init();
        }));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
            .dimensions(right, y, 160, 20)
            .build());
    }

    @Override
    public void close() {
        saveConfig();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Avoid Minecraft's blur path here to prevent multi-blur-per-frame crashes.
        context.fillGradient(0, 0, width, height, 0xCC0E1118, 0xCC161E2B);
        int panelLeft = width / 2 - 178;
        int panelRight = width / 2 + 178;
        int panelTop = 22;
        int panelBottom = height - 22;
        context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCC0A0F17);
        context.drawBorder(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFF3A4A66);

        super.render(context, mouseX, mouseY, delta);

        int x = width / 2 - 165;
        int y = 8;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, y, 0xFFFFFFFF);
        y += 14;
        if (!pathManager.isAvailable()) {
            context.drawTextWithShadow(textRenderer, "Baritone API not found at runtime.", x, y, 0xFFFF7777);
            y += 12;
            context.drawTextWithShadow(textRenderer, "Install baritone-api-fabric for your Minecraft version in Prism.", x, y, 0xFFB8C0D0);
            return;
        }

        String tabName = selectedTab == 0 ? "Control" : selectedTab == 1 ? "Movement" : selectedTab == 2 ? "Safety" : "Render";
        context.drawTextWithShadow(textRenderer, "Tab: " + tabName + " | Goal: " + pathManager.getCurrentGoalText(), x, y, 0xFFB8C0D0);
        y += 14;
        String target = controller.getLastTarget() == null ? "none" : controller.getLastTarget().toShortString();
        context.drawTextWithShadow(textRenderer, "Target: " + target + " | Follow: " + controller.getFollowTargetName(), x, y, 0xFFFFFFFF);
    }

    private void applyNumericSettings() {
        try {
            pathManager.setPrimaryTimeoutMs(Long.parseLong(timeoutField.getText().trim()));
        } catch (NumberFormatException ignored) {
        }

        try {
            pathManager.setRandomLooking(Double.parseDouble(randomLookingField.getText().trim()));
        } catch (NumberFormatException ignored) {
        }

        saveConfig();
        init();
    }

    private void saveConfig() {
        config.selectedTab = selectedTab;
        onConfigChanged.run();
    }

    private ButtonWidget toggleButton(int x, int y, Text text, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(text, action)
            .dimensions(x, y, 160, 20)
            .build();
    }

    private Text buttonLabel(String label, boolean value) {
        return Text.literal(label + ": " + (value ? "ON" : "OFF"));
    }
}
