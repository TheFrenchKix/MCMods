package com.example.macromod.proxy;

import com.example.macromod.ui.easyblock.Anim;
import com.example.macromod.ui.easyblock.BasePopupScreen;
import com.example.macromod.ui.easyblock.EasyBlockGui;
import com.example.macromod.ui.easyblock.RoundedRectRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Add / edit a single proxy entry in the proxy list.
 * editIndex = -1 means adding a new proxy; >= 0 means editing an existing one.
 */
public class ProxyScreen extends BasePopupScreen {

    // -- Layout constants --------------------------------------------------
    private static final int R       = 10;
    private static final int HH      = 30;
    private static final int FH      = 40;
    private static final int FIELD_H = 16;
    private static final int PH      = 310;
    private static final int LABEL_X = 14;
    private static final int FIELD_X = 110;

    private static final String[] PROTOCOLS = {
        ProxyConfig.PROTO_SOCKS4, ProxyConfig.PROTO_SOCKS5,
        ProxyConfig.PROTO_HTTP,   ProxyConfig.PROTO_HTTPS
    };

    // -- State -------------------------------------------------------------
    private int px, py, pw;
    private final int editIndex;

    private TextFieldWidget nameField, urlField, hostField, portField, userField, passField;

    private int     protoIndex;
    private boolean hoverProtoLeft, hoverProtoRight;

    private enum TestState { NOT_TESTED, TESTING, VALID, INVALID }
    private TestState testState = TestState.NOT_TESTED;
    private String    testInfo  = "";

    private boolean hoverTest, hoverSave, hoverCancel, hoverClear;

    // -- Constructor -------------------------------------------------------

    public ProxyScreen(Screen parent, int editIndex) {
        super(Text.literal(editIndex < 0 ? "Add Proxy" : "Edit Proxy"), parent);
        this.editIndex = editIndex;
        ProxyConfig cfg = loadCfg();
        protoIndex = protoIndexOf(cfg.getProtocol());
    }

    private ProxyConfig loadCfg() {
        List<ProxyConfig> proxies = ProxyManager.getInstance().getProxies();
        return (editIndex >= 0 && editIndex < proxies.size())
            ? proxies.get(editIndex) : new ProxyConfig();
    }

    private static int protoIndexOf(String proto) {
        if (proto == null) return 1;
        for (int i = 0; i < PROTOCOLS.length; i++)
            if (PROTOCOLS[i].equalsIgnoreCase(proto)) return i;
        return 1;
    }

    // -- Init --------------------------------------------------------------

    @Override
    protected void init() {
        pw = Math.min(520, Math.max(340, width - 60));
        px = (width - pw) / 2;
        py = (height - PH) / 2;

        ProxyConfig cfg  = loadCfg();
        int fieldX = px + FIELD_X;
        int fieldW = pw - FIELD_X - 14;
        int ct = py + HH + 8;

        // Row 0 - Name (optional nickname)
        nameField = makeField(fieldX, ct + 10, fieldW, "Nickname (optional)", cfg.getName(), 64);

        // URL paste row (full width, below name)
        String urlInit = !cfg.getHost().isEmpty() ? cfg.toUrl() : "";
        urlField = makeField(px + LABEL_X, ct + 44, pw - LABEL_X * 2, "Paste proxy URL", urlInit, 512);

        // Protocol cycle - drawn as buttons, no text field

        // Manual fields
        hostField = makeField(fieldX, ct + 96,  fieldW, "Host",               cfg.getHost(),     253);
        portField = makeField(fieldX, ct + 122, fieldW, "Port",
            cfg.getPort() > 0 ? String.valueOf(cfg.getPort()) : "1080", 5);
        userField = makeField(fieldX, ct + 148, fieldW, "Username (optional)", cfg.getUsername(), 128);
        passField = makeField(fieldX, ct + 174, fieldW, "Password (optional)", cfg.getPassword(), 128);
    }

    private TextFieldWidget makeField(int x, int y, int w, String hint, String val, int max) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, FIELD_H, Text.literal(hint));
        f.setMaxLength(max);
        f.setText(val != null ? val : "");
        f.setDrawsBackground(false);
        addSelectableChild(f);
        return f;
    }

    // -- Drawing -----------------------------------------------------------

    @Override
    protected void drawScreen(DrawContext ctx, int mx, int my, float delta) {
        hoverTest = hoverSave = hoverCancel = hoverClear = false;
        hoverProtoLeft = hoverProtoRight = false;

        RoundedRectRenderer.draw(ctx, px, py, pw, PH, R, EasyBlockGui.C_BG);

        // Header
        ctx.fill(px, py, px + pw, py + HH, 0xFF101018);
        ctx.fill(px, py + HH - 1, px + pw, py + HH, EasyBlockGui.C_DIVIDER);
        String icon = editIndex < 0 ? "\u00A7b\u271A " : "\u00A7b\u270E ";
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(icon + "\u00A7f\u00A7l" + (editIndex < 0 ? "Add Proxy" : "Edit Proxy")),
            px + LABEL_X, py + (HH - 8) / 2, EasyBlockGui.C_TEXT);

        ctx.fill(px, py + PH - FH, px + pw, py + PH - FH + 1, EasyBlockGui.C_DIVIDER);

        drawFields(ctx, mx, my);
        drawStatus(ctx);
        drawFooter(ctx, mx, my);
    }

    private void drawFields(DrawContext ctx, int mx, int my) {
        int lx = px + LABEL_X;
        int ct = py + HH + 8;

        // Name
        drawLabel(ctx, lx, ct + 10, "Name");
        drawFieldBg(ctx, nameField);
        nameField.render(ctx, mx, my, 0);

        // URL
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Paste URL  (protocol://[user:pass@]host:port)"),
            lx, ct + 36, 0xFF888899);
        drawFieldBg(ctx, urlField);
        urlField.render(ctx, mx, my, 0);

        // Protocol cycle
        drawLabel(ctx, lx, ct + 70, "Protocol");
        drawProtocolCycle(ctx, mx, my, ct + 70);

        // Host
        drawLabel(ctx, lx, ct + 96, "Host");
        drawFieldBg(ctx, hostField);
        hostField.render(ctx, mx, my, 0);

        // Port
        drawLabel(ctx, lx, ct + 122, "Port");
        drawFieldBg(ctx, portField);
        portField.render(ctx, mx, my, 0);

        // Username
        drawLabel(ctx, lx, ct + 148, "Username");
        drawFieldBg(ctx, userField);
        userField.render(ctx, mx, my, 0);

        // Password (masked when unfocused)
        drawLabel(ctx, lx, ct + 174, "Password");
        drawFieldBg(ctx, passField);
        if (!passField.isFocused() && !passField.getText().isEmpty()) {
            String masked = "\u2022".repeat(Math.min(passField.getText().length(), 30));
            ctx.drawTextWithShadow(textRenderer, Text.literal(masked),
                passField.getX() + 2, passField.getY() + 4, EasyBlockGui.C_TEXT);
        } else {
            passField.render(ctx, mx, my, 0);
        }
    }

    private void drawProtocolCycle(DrawContext ctx, int mx, int my, int rowY) {
        String proto  = PROTOCOLS[protoIndex].toUpperCase();
        int arrowW = 18, labelW = textRenderer.getWidth(proto) + 20;
        int startX = px + FIELD_X, btnH = 14, btnY = rowY + 4;

        hoverProtoLeft = mx >= startX && mx < startX + arrowW && my >= btnY && my < btnY + btnH;
        RoundedRectRenderer.draw(ctx, startX, btnY, arrowW, btnH, 4,
            hoverProtoLeft ? 0xFF2A2A40 : 0xFF1A1A28);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u25C4"),
            startX + arrowW / 2, btnY + 3, EasyBlockGui.C_TEXT2);

        ctx.fill(startX + arrowW, btnY, startX + arrowW + labelW, btnY + btnH, 0xFF1A1A28);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(proto),
            startX + arrowW + labelW / 2, btnY + 3, 0xFF00DDFF);

        int rx = startX + arrowW + labelW;
        hoverProtoRight = mx >= rx && mx < rx + arrowW && my >= btnY && my < btnY + btnH;
        RoundedRectRenderer.draw(ctx, rx, btnY, arrowW, btnH, 4,
            hoverProtoRight ? 0xFF2A2A40 : 0xFF1A1A28);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u25BA"),
            rx + arrowW / 2, btnY + 3, EasyBlockGui.C_TEXT2);
    }

    private void drawLabel(DrawContext ctx, int x, int y, String text) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(text), x, y + 4, EasyBlockGui.C_TEXT2);
    }

    private void drawFieldBg(DrawContext ctx, TextFieldWidget f) {
        int fx = f.getX() - 2, fy = f.getY() - 2;
        int fw = f.getWidth() + 4, fh = FIELD_H + 4;
        ctx.fill(fx, fy, fx + fw, fy + fh, 0xFF1A1A28);
        ctx.fill(fx, fy + fh - 1, fx + fw, fy + fh, EasyBlockGui.C_DIVIDER);
    }

    private void drawStatus(DrawContext ctx) {
        int sy = py + PH - FH - 20;
        String text; int color;
        switch (testState) {
            case TESTING -> { text = "Testing...";                    color = 0xFFFFDD00; }
            case VALID   -> { text = "\u2705 Valid  " + testInfo;     color = 0xFF55FF55; }
            case INVALID -> { text = "\u274C Invalid  " + testInfo;   color = 0xFFFF5555; }
            default      -> { text = "Not tested";                    color = EasyBlockGui.C_TEXT3; }
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(text), px + pw / 2, sy, color);
    }

    private void drawFooter(DrawContext ctx, int mx, int my) {
        int btnW = 76, btnH = 22, gap = 8;
        int btnY = py + PH - FH + (FH - btnH) / 2;
        int sx   = px + (pw - (btnW * 4 + gap * 3)) / 2;

        hoverTest   = drawBtn(ctx, sx,                     btnY, btnW, btnH, "Test",
            mx, my, testState == TestState.TESTING ? 0xFF555555 : EasyBlockGui.C_ACCENT);
        hoverClear  = drawBtn(ctx, sx + (btnW + gap),      btnY, btnW, btnH, "Clear",
            mx, my, 0xFF555566);
        hoverSave   = drawBtn(ctx, sx + (btnW + gap) * 2,  btnY, btnW, btnH, "Save",
            mx, my, 0xFF267A26);
        hoverCancel = drawBtn(ctx, sx + (btnW + gap) * 3,  btnY, btnW, btnH, "Cancel",
            mx, my, 0xFF882222);
    }

    private boolean drawBtn(DrawContext ctx, int x, int y, int w, int h,
                            String lbl, int mx, int my, int base) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        RoundedRectRenderer.draw(ctx, x, y, w, h, 5, hover ? brighten(base) : base);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(lbl),
            x + w / 2, y + (h - 8) / 2, EasyBlockGui.C_TEXT);
        return hover;
    }

    private static int brighten(int c) {
        return (c & 0xFF000000)
            | (Math.min(255, ((c >> 16) & 0xFF) + 30) << 16)
            | (Math.min(255, ((c >>  8) & 0xFF) + 30) <<  8)
            |  Math.min(255, ( c        & 0xFF) + 30);
    }

    // -- Input -------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        if (click.button() == 0) {
            if (hoverProtoLeft)  { cycleProto(-1); return true; }
            if (hoverProtoRight) { cycleProto(+1); return true; }
            if (hoverTest && testState != TestState.TESTING) { startTest(); return true; }
            if (hoverClear)  { doClear();   return true; }
            if (hoverSave)   { doSave();    return true; }
            if (hoverCancel) { animClose(); return true; }
        }
        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (key.key() == 256) { animClose(); return true; }
        return super.keyPressed(key);
    }

    private void cycleProto(int dir) {
        protoIndex = (protoIndex + PROTOCOLS.length + dir) % PROTOCOLS.length;
        String url = urlField.getText().trim();
        if (url.contains("://"))
            urlField.setText(PROTOCOLS[protoIndex] + url.substring(url.indexOf("://")));
    }

    // -- Actions -----------------------------------------------------------

    private ProxyConfig buildConfig() {
        ProxyConfig cfg = new ProxyConfig();
        String urlText = urlField.getText().trim();
        if (!urlText.isEmpty() && urlText.contains("://")) {
            if (!cfg.parseFromUrl(urlText)) return null;
        } else {
            String host = hostField.getText().trim();
            if (host.isEmpty()) { testInfo = "Host is empty"; return null; }
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
                if (port < 1 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                testInfo = "Invalid port"; return null;
            }
            cfg.setProtocol(PROTOCOLS[protoIndex]);
            cfg.setHost(host);
            cfg.setPort(port);
            cfg.setUsername(userField.getText().trim());
            cfg.setPassword(passField.getText());
        }
        cfg.setName(nameField.getText().trim());
        return cfg;
    }

    private void startTest() {
        testInfo = "";
        ProxyConfig tcfg = buildConfig();
        if (tcfg == null) {
            testState = TestState.INVALID;
            if (testInfo.isEmpty()) testInfo = "Invalid URL format";
            return;
        }
        testState = TestState.TESTING;
        ProxyTester.testAsync(tcfg).thenAccept(result -> {
            if (client != null) client.execute(() -> {
                if (result.success) {
                    testState = TestState.VALID;
                    testInfo  = "(" + result.latencyMs + "ms)";
                    if (result.detectedIp != null && !result.detectedIp.isEmpty())
                        testInfo += "  IP: " + result.detectedIp;
                    ProxyManager.getInstance().setLastTestSuccess(true);
                } else {
                    testState = TestState.INVALID;
                    testInfo  = result.error != null ? result.error : "";
                    ProxyManager.getInstance().setLastTestSuccess(false);
                }
            });
        });
    }

    private void doSave() {
        if (testState != TestState.VALID) {
            testState = TestState.INVALID;
            testInfo  = "Test the proxy before saving!";
            return;
        }
        ProxyConfig built = buildConfig();
        if (built == null) {
            testState = TestState.INVALID;
            if (testInfo.isEmpty()) testInfo = "Could not parse config";
            return;
        }
        ProxyManager pm = ProxyManager.getInstance();
        if (editIndex < 0) {
            pm.addProxy(built);
            if (pm.getProxies().size() == 1) pm.setActiveIndex(0);
        } else {
            pm.getProxies().set(editIndex, built);
        }
        pm.save();
        animClose();
    }

    private void doClear() {
        nameField.setText("");
        urlField.setText("");
        hostField.setText("");
        portField.setText("1080");
        userField.setText("");
        passField.setText("");
        testState = TestState.NOT_TESTED;
        testInfo  = "";
        protoIndex = 1;
    }
}