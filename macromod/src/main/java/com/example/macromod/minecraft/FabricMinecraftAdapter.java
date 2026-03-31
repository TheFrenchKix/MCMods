package com.example.macromod.minecraft;

import com.example.macromod.data.blockpos.BaseBlockPos;
import com.example.macromod.data.blocks.BlockWrapper;
import com.example.macromod.data.items.ItemUtils;
import com.example.macromod.data.items.wrapper.ItemBlockWrapper;
import com.example.macromod.data.items.wrapper.ItemStackWrapper;
import com.example.macromod.data.items.wrapper.ItemWrapper;
import com.example.macromod.math.vectors.vec2.Vector2d;
import com.example.macromod.math.vectors.vec3.Vector3d;
import com.example.macromod.player.PlayerInputConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class FabricMinecraftAdapter implements MinecraftAdapter {

    private static final int KEY_FORWARD = 1;
    private static final int KEY_BACKWARD = 2;
    private static final int KEY_LEFT = 3;
    private static final int KEY_RIGHT = 4;
    private static final int KEY_SPRINT = 5;
    private static final int KEY_SNEAK = 6;
    private static final int KEY_JUMP = 7;
    private static final int KEY_USE = 8;
    private static final int KEY_ATTACK = 9;

    private MouseChangeInterceptor mouseChangeInterceptor;

    private MinecraftClient getClient() {
        return MinecraftClient.getInstance();
    }

    private ClientPlayerEntity getPlayer() {
        return getClient().player;
    }

    @Override
    public boolean hasPlayer() {
        return getPlayer() != null;
    }

    @Override
    public boolean isPlayerCreativeMode() {
        ClientPlayerEntity player = getPlayer();
        return player != null && player.isCreative();
    }

    @Override
    public Vector3d getPlayerHeadPosition() {
        ClientPlayerEntity player = getPlayer();
        if (player == null) return null;
        Vec3d eye = player.getEyePos();
        return new Vector3d(eye.x, eye.y, eye.z);
    }

    @Override
    public Vector2d getPlayerHeadPositionXZ() {
        ClientPlayerEntity player = getPlayer();
        if (player == null) return null;
        Vec3d eye = player.getEyePos();
        return new Vector2d(eye.x, eye.z);
    }

    @Override
    public BaseBlockPos getPlayerBlockPosition() {
        ClientPlayerEntity player = getPlayer();
        if (player == null) return null;
        BlockPos pos = player.getBlockPos();
        return new BaseBlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public Vector3d getPlayerPosition() {
        ClientPlayerEntity player = getPlayer();
        if (player == null) return null;
        Vec3d pos = player.getPos();
        return new Vector3d(pos.x, pos.y, pos.z);
    }

    @Override
    public Vector3d getPlayerMotion() {
        ClientPlayerEntity player = getPlayer();
        if (player == null) return null;
        Vec3d vel = player.getVelocity();
        return new Vector3d(vel.x, vel.y, vel.z);
    }

    @Override
    public float getPlayerRotationYaw() {
        ClientPlayerEntity player = getPlayer();
        return player != null ? player.getYaw() : 0.0f;
    }

    @Override
    public float getPlayerRotationPitch() {
        ClientPlayerEntity player = getPlayer();
        return player != null ? player.getPitch() : 0.0f;
    }

    @Override
    public void setPlayerRotation(float yaw, float pitch) {
        ClientPlayerEntity player = getPlayer();
        if (player != null) {
            player.setYaw(yaw);
            player.setPitch(pitch);
        }
    }

    @Override
    public void setCameraRotation(float yaw, float pitch) {
        setPlayerRotation(yaw, pitch);
    }

    @Override
    public Vector3d getLookDir() {
        ClientPlayerEntity player = getPlayer();
        if (player == null) return null;
        Vec3d look = player.getRotationVec(1.0f);
        return new Vector3d(look.x, look.y, look.z);
    }

    @Override
    public void setMouseChangeInterceptor(MouseChangeInterceptor interceptor) {
        this.mouseChangeInterceptor = interceptor;
    }

    @Override
    public float getMouseSensitivity() {
        return getClient().options.getMouseSensitivity().getValue().floatValue();
    }

    @Override
    public double getMouseDX() {
        return 0;
    }

    @Override
    public double getMouseDY() {
        return 0;
    }

    @Override
    public void setInput(int keyCode, boolean down) {
        KeyBinding binding = switch (keyCode) {
            case KEY_FORWARD -> getClient().options.forwardKey;
            case KEY_BACKWARD -> getClient().options.backKey;
            case KEY_LEFT -> getClient().options.leftKey;
            case KEY_RIGHT -> getClient().options.rightKey;
            case KEY_SPRINT -> getClient().options.sprintKey;
            case KEY_SNEAK -> getClient().options.sneakKey;
            case KEY_JUMP -> getClient().options.jumpKey;
            case KEY_USE -> getClient().options.useKey;
            case KEY_ATTACK -> getClient().options.attackKey;
            default -> null;
        };
        if (binding != null) {
            binding.setPressed(down);
        }
    }

    @Override
    public void setPlayerSprinting(boolean sprint) {
        ClientPlayerEntity player = getPlayer();
        if (player != null) {
            player.setSprinting(sprint);
        }
    }

    @Override
    public InputBinding getKeyBinding(PlayerInputConfig.InputType inputType) {
        return switch (inputType) {
            case WALK_FORWARD -> new FabricInputBinding(KEY_FORWARD, getClient().options.forwardKey);
            case WALK_BACKWARD -> new FabricInputBinding(KEY_BACKWARD, getClient().options.backKey);
            case WALK_LEFT -> new FabricInputBinding(KEY_LEFT, getClient().options.leftKey);
            case WALK_RIGHT -> new FabricInputBinding(KEY_RIGHT, getClient().options.rightKey);
            case SPRINT -> new FabricInputBinding(KEY_SPRINT, getClient().options.sprintKey);
            case SNEAK -> new FabricInputBinding(KEY_SNEAK, getClient().options.sneakKey);
            case JUMP -> new FabricInputBinding(KEY_JUMP, getClient().options.jumpKey);
            case PLACE_BLOCK -> new FabricInputBinding(KEY_USE, getClient().options.useKey);
            case BREAK_BLOCK -> new FabricInputBinding(KEY_ATTACK, getClient().options.attackKey);
            case INTERACT -> new FabricInputBinding(KEY_USE, getClient().options.useKey);
        };
    }

    @Override
    public boolean isPlayerOnGround() {
        ClientPlayerEntity player = getPlayer();
        return player != null && player.isOnGround();
    }

    @Override
    public float getPlayerHealth() {
        ClientPlayerEntity player = getPlayer();
        return player != null ? player.getHealth() : 0.0f;
    }

    @Override
    public void sendMessage(String msg) {
        ClientPlayerEntity player = getPlayer();
        if (player != null) {
            player.sendMessage(Text.literal(msg), false);
        }
    }

    @Override
    public List<ItemStackWrapper> getHotbarItems() {
        List<ItemStackWrapper> items = new ArrayList<>();
        ClientPlayerEntity player = getPlayer();
        if (player == null) return items;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                int id = Registries.ITEM.getRawId(item);
                ItemWrapper wrapped = ItemUtils.getItemLibrary() != null
                        ? ItemUtils.getItemLibrary().getItemById(id)
                        : new ItemWrapper(id, Registries.ITEM.getId(item).toString());
                items.add(new ItemStackWrapper(wrapped, stack.getCount(), i));
            }
        }
        return items;
    }

    @Override
    public void selectHotbarSlot(int slot) {
        ClientPlayerEntity player = getPlayer();
        if (player != null && slot >= 0 && slot <= 8) {
            player.getInventory().selectedSlot = slot;
        }
    }

    @Override
    public List<BlockWrapper> getBlocks() {
        List<BlockWrapper> blocks = new ArrayList<>();
        for (Block block : Registries.BLOCK) {
            int id = Registries.BLOCK.getRawId(block);
            Identifier name = Registries.BLOCK.getId(block);
            boolean isNormalCube = block.getDefaultState().isSolid();
            blocks.add(new BlockWrapper(id, name.toString(), isNormalCube));
        }
        return blocks;
    }

    @Override
    public List<ItemWrapper> getItems() {
        List<ItemWrapper> items = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            int id = Registries.ITEM.getRawId(item);
            Identifier name = Registries.ITEM.getId(item);
            if (item instanceof BlockItem) {
                items.add(new ItemBlockWrapper(id, name.toString()));
            } else {
                items.add(new ItemWrapper(id, name.toString()));
            }
        }
        return items;
    }

    @Override
    public int getBlockId(BaseBlockPos pos) {
        if (getClient().world == null) return -1;
        BlockState state = getClient().world.getBlockState(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        return Registries.BLOCK.getRawId(state.getBlock());
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        if (getClient().world == null) return false;
        return getClient().world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public int getItemIdFromBlock(BlockWrapper block) {
        Identifier id = Identifier.tryParse(block.getName());
        if (id == null) return -1;
        Block mcBlock = Registries.BLOCK.get(id);
        return Registries.ITEM.getRawId(mcBlock.asItem());
    }

    @Override
    public int getBlockIdFromItem(ItemBlockWrapper item) {
        Identifier id = Identifier.tryParse(item.getName());
        if (id == null) return -1;
        Item mcItem = Registries.ITEM.get(id);
        if (mcItem instanceof BlockItem blockItem) {
            return Registries.BLOCK.getRawId(blockItem.getBlock());
        }
        return -1;
    }

    @Override
    public String getBlockFacing(BaseBlockPos position) {
        if (getClient().world == null) return "y";
        BlockState state = getClient().world.getBlockState(new BlockPos(position.getX(), position.getY(), position.getZ()));
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            return facing.getAxis().asString();
        }
        if (state.contains(Properties.AXIS)) {
            return state.get(Properties.AXIS).asString();
        }
        return "y";
    }

    @Override
    public boolean isDoorOpen(BaseBlockPos position) {
        if (getClient().world == null) return false;
        BlockState state = getClient().world.getBlockState(new BlockPos(position.getX(), position.getY(), position.getZ()));
        return state.contains(Properties.OPEN) && state.get(Properties.OPEN);
    }

    @Override
    public boolean isBlockPassable(BlockWrapper block, BaseBlockPos pos) {
        if (getClient().world == null) return false;
        BlockPos mcPos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        BlockState state = getClient().world.getBlockState(mcPos);
        return state.isAir() || state.getCollisionShape(getClient().world, mcPos).isEmpty();
    }

    @Override
    public float getBreakDuration(ItemWrapper item, BlockWrapper block) {
        ClientPlayerEntity player = getPlayer();
        if (player == null || getClient().world == null) return 100.0f;

        Identifier id = Identifier.tryParse(block.getName());
        if (id == null) return 100.0f;
        Block mcBlock = Registries.BLOCK.get(id);
        if (mcBlock == null) return 100.0f;

        BlockPos probePos = player.getBlockPos();
        BlockState state = mcBlock.getDefaultState();
        float hardness = state.getHardness(getClient().world, probePos);
        if (hardness < 0.0f) return 9999.0f;

        float speed = player.getBlockBreakingSpeed(state);
        if (speed <= 0.0f) return 9999.0f;

        return Math.max(1.0f, (hardness * 30.0f) / speed);
    }

}
