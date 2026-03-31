package com.example.macromod.pathfinding.nodes;

import java.util.ConcurrentModificationException;
import java.util.Map;
import com.example.macromod.data.blockpos.BaseBlockPos;
import com.example.macromod.math.vectors.vec3.Vector3d;
import com.example.macromod.rendering.Color;
import com.example.macromod.rendering.Renderable;
import com.example.macromod.rendering.Renderer;

public class NodeRenderable implements Renderable {


    private final Map<BaseBlockPos, Node> nodes;


    public NodeRenderable(Map<BaseBlockPos, Node> nodes) {
        this.nodes = nodes;
    }


    @Override
    public void render(Renderer renderer) {
        renderAABB(renderer);
    }


    private void renderAABB(Renderer renderer) {
        renderer.beginBoxes(2);
        final Vector3d pos = new Vector3d();
        try {
            for (Node node : nodes.values()) {
                BaseBlockPos nodePos = node.getPos();
                pos.set(nodePos.getX(), nodePos.getY(), nodePos.getZ());
                renderer.drawBoxOpen(pos, (node.isOpen() ? Color.WHITE : Color.GRAY));
            }
        } catch (ConcurrentModificationException e) {
            // ignore
        }
        renderer.end();
    }

}