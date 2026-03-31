You are designing a pathfinding system for a 2D or 3D grid-based world (similar to Minecraft). The goal is to implement diagonal movement in a way that produces natural, efficient, and realistic paths while avoiding zig-zag artifacts and invalid movement through obstacles.

### Core Requirements

1. The pathfinding algorithm must support both straight and diagonal movement:

   * Straight moves: (±1, 0), (0, ±1)
   * Diagonal moves: (±1, ±1)

2. Movement costs must reflect real distances:

   * Straight movement cost = 1
   * Diagonal movement cost = √2 (~1.414)

3. The heuristic function must be consistent with diagonal movement:

   * Use the Octile distance heuristic:
     dx = abs(x1 - x2)
     dz = abs(z1 - z2)
     h = max(dx, dz) + (√2 - 1) * min(dx, dz)

4. Prevent corner cutting:

   * A diagonal move (x+1, z+1) is only valid if both adjacent orthogonal neighbors are walkable:

     * (x+1, z) must be walkable
     * (x, z+1) must be walkable
   * This prevents moving diagonally through blocked corners or walls.

5. Ensure walkability checks include:

   * The target tile must be free (no obstacle)
   * The entity must have enough clearance (e.g., height in 3D worlds)
   * The tile must have valid ground support if required

6. For 3D/block-based worlds:

   * Diagonal movement must also respect vertical constraints:

     * Allow stepping up by at most 1 block
     * Ensure headroom is clear
     * Ensure landing positions are valid
   * Diagonal movement across height differences must validate both adjacent axes independently

7. Avoid zig-zag paths:

   * Ensure diagonal movement is not artificially penalized or equalized incorrectly
   * Proper cost + heuristic alignment should naturally favor straight diagonals when optimal

8. Optional path smoothing:

   * After computing the path, remove unnecessary intermediate nodes
   * Use line-of-sight checks to simplify jagged paths into smoother ones

### Expected Outcome

* Paths should be:

  * Direct and efficient
  * Visually smooth (minimal zig-zagging)
  * Physically valid (no clipping through obstacles)
  * Consistent with movement rules of the world

### Constraints

* The algorithm must remain performant for large grids
* It should be compatible with A* or similar graph search algorithms
* It should handle both open areas and tight obstacle spaces correctly

### Bonus (Optional Enhancements)

* Add higher cost for difficult terrain (water, slopes, hazards)
* Support entity-specific movement rules
* Implement hierarchical or cached pathfinding for optimization

Generate clean, well-structured logic or code that follows these principles and clearly separates:

* Neighbor generation
* Movement validation
* Cost calculation
* Heuristic estimation

Ensure the final solution avoids unnatural zig-zag movement and produces optimal diagonal paths when appropriate.