package com.example.macromod.pathfinding.goal;

import com.example.macromod.data.blockpos.BaseBlockPos;
import com.example.macromod.math.vectors.vec3.Vector3d;
import com.example.macromod.pathfinding.actions.ActionCosts;
import com.example.macromod.rendering.Color;
import com.example.macromod.rendering.Renderable;

public class ExactGoal extends Goal {


    public final BaseBlockPos pos;


    public ExactGoal(BaseBlockPos pos) {
        this.pos = pos;
    }


    @Override
    public boolean reached(BaseBlockPos pos) {
        return this.pos.getX() == pos.getX() && this.pos.getY() == pos.getY() && this.pos.getZ() == pos.getZ();
    }


    @Override
    public double calcHCost(BaseBlockPos pos) {
        final int gx = this.pos.getX();
        final int gz = this.pos.getZ();
        final int px = pos.getX();
        final int pz = pos.getZ();
        int dMax = Math.max(Math.abs(px - gx), Math.abs(pz - gz));
        int dMin = Math.min(Math.abs(px - gx), Math.abs(pz - gz));

        final double cost_mult_diagonal = ActionCosts.get().WALK_SPRINT_DIAGONAL / ActionCosts.get().WALK_SPRINT_STRAIGHT;
        // Keep a guaranteed lower bound: horizontal octile only.
        // Vertical actions have multiple mechanics (step, fall, pillar, jump), and
        // using a too-strong Y term can overestimate and break A* guarantees.
        return (dMin * cost_mult_diagonal + (dMax - dMin)) * ActionCosts.get().WALK_SPRINT_STRAIGHT;
    }


    @Override
    public String goalString() {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }


    @Override
    public Renderable createRenderable() {
        final Vector3d posMin = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
        final Vector3d posMax = new Vector3d(pos.getX(), pos.getY(), pos.getZ()).add(1, 2, 1);
        return renderer -> {
            renderer.beginBoxes(3);
            renderer.drawBoxOpen(posMin, posMax, Color.GREEN);
            renderer.end();
        };
    }

}
