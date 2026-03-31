package com.example.macromod.pathfinding.path;

import java.util.Collections;
import java.util.List;
import com.example.macromod.pathfinding.actions.ActionCosts;
import com.example.macromod.pathfinding.nodes.Node;

/**
 * A failed path (a path that does not contain any nodes).
 */
public class EmptyPath implements Path {


    @Override
    public double getCost() {
        return ActionCosts.get().COST_INFINITE;
    }


    @Override
    public List<Node> getNodes() {
        return Collections.emptyList();
    }


    @Override
    public boolean reachedGoal() {
        return false;
    }


    @Override
    public Node getFirstNode() {
        return null;
    }


    @Override
    public Node getLastNode() {
        return null;
    }


}
