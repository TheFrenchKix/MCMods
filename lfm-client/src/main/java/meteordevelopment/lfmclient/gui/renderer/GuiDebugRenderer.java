/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.gui.renderer;

import lfmdevelopment.lfmclient.lfmClient;
import lfmdevelopment.lfmclient.gui.utils.Cell;
import lfmdevelopment.lfmclient.gui.widgets.WWidget;
import lfmdevelopment.lfmclient.gui.widgets.containers.WContainer;
import lfmdevelopment.lfmclient.renderer.MeshBuilder;
import lfmdevelopment.lfmclient.renderer.MeshRenderer;
import lfmdevelopment.lfmclient.renderer.lfmRenderPipelines;
import lfmdevelopment.lfmclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;

public class GuiDebugRenderer {
    private static final Color CELL_COLOR = new Color(25, 225, 25);
    private static final Color WIDGET_COLOR = new Color(25, 25, 225);

    private final MeshBuilder mesh = new MeshBuilder(lfmRenderPipelines.WORLD_COLORED_LINES);

    public void render(WWidget widget) {
        if (widget == null) return;

        mesh.begin();
        renderWidget(widget);
        mesh.end();

        MeshRenderer.begin()
            .attachments(MinecraftClient.getInstance().getFramebuffer())
            .pipeline(lfmRenderPipelines.WORLD_COLORED_LINES)
            .mesh(mesh)
            .end();
    }

    public void mouseReleased(WWidget widget, Click click, int i) {
        if (widget == null) return;

        lfmClient.LOG.info("{} {}", widget.getClass(), i);

        if (widget instanceof WContainer container) {
            for (Cell<?> cell : container.cells) {
                if (cell.widget().isOver(click.x(), click.y())) {
                    mouseReleased(cell.widget(), click, i + 1);
                }
            }
        }
    }

    private void renderWidget(WWidget widget) {
        lineBox(widget.x, widget.y, widget.width, widget.height, WIDGET_COLOR);

        if (widget instanceof WContainer container) {
            for (Cell<?> cell : container.cells) {
                lineBox(cell.x, cell.y, cell.width, cell.height, CELL_COLOR);
                renderWidget(cell.widget());
            }
        }
    }

    private void lineBox(double x, double y, double width, double height, Color color) {
        line(x, y, x + width, y, color);
        line(x + width, y, x + width, y + height, color);
        line(x, y, x, y + height, color);
        line(x, y + height, x + width, y + height, color);
    }

    private void line(double x1, double y1, double x2, double y2, Color color) {
        mesh.ensureLineCapacity();

        mesh.line(
            mesh.vec3(x1, y1, 0).color(color).next(),
            mesh.vec3(x2, y2, 0).color(color).next()
        );
    }
}
