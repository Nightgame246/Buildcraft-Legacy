package com.thepigcat.buildcraft.datagen.assets;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.PipesRegistry;
import com.thepigcat.buildcraft.api.blocks.ExtractingPipeBlock;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.content.blocks.DiamondItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.ExtractingKinesisPipeBlock;
import com.thepigcat.buildcraft.content.blocks.IronFluidPipeBlock;
import com.thepigcat.buildcraft.content.blocks.KinesisPipeBlock;
import com.thepigcat.buildcraft.api.pipes.Pipe;
import com.thepigcat.buildcraft.content.blocks.CrateBlock;
import com.thepigcat.buildcraft.content.blocks.TankBlock;
import com.thepigcat.buildcraft.registries.BCBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.*;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class BCBlockStateProvider extends BlockStateProvider {
    public BCBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, BuildcraftLegacy.MODID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        crateBlock(BCBlocks.CRATE.get());
        pillarBlock(BCBlocks.QUARRY.get(), inDir(blockTexture(BCBlocks.QUARRY.get()), "machine"));
        tankBlock(BCBlocks.TANK.get());
        engineBlock(BCBlocks.REDSTONE_ENGINE.get());
        engineBlock(BCBlocks.STIRLING_ENGINE.get());
        engineBlock(BCBlocks.COMBUSTION_ENGINE.get());

        for (Block block : BCBlocks.BLOCKS.getRegistry().get()) {
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            if (path.equals("iron_pipe")) {
                ironItemPipeBlock(block);
            } else if (block instanceof DiamondItemPipeBlock) {
                diamondItemPipeBlock(block);
            } else if (block instanceof ExtractingKinesisPipeBlock) {
                extractingKinesisPipeBlock(block);
            } else if (block instanceof KinesisPipeBlock) {
                kinesisPipeBlock(block);
            } else if (block instanceof ExtractingPipeBlock) {
                extractingPipeBlock(block);
            } else if (block instanceof IronFluidPipeBlock) {
                ironItemPipeBlock(block);
            } else if (block instanceof PipeBlock) {
                pipeBlock(block);
            }
        }
    }

    private void diamondItemPipeBlock(Block block) {
        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(block);
        Pipe pipe = PipesRegistry.PIPES.get(loc.getPath());
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block);
        // Texture order: [base=0, down=1, up=2, north=3, south=4, west=5, east=6]
        // Rotation order mirrors the default pipe blockstate definition
        Direction[] dirs = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        String[] dirNames = {"down", "up", "north", "east", "south", "west"};
        int[] texIdx  = {1, 2, 3, 6, 4, 5};
        int[] rotX    = {0, 180, 90, 90, 90, 90};
        int[] rotY    = {0,   0, 180, 270, 0, 90};
        for (int i = 0; i < 6; i++) {
            builder.part()
                    .modelFile(diamondPipeConnectionModel(loc, dirNames[i], pipe.textures().get(texIdx[i])))
                    .rotationX(rotX[i]).rotationY(rotY[i]).addModel()
                    .condition(PipeBlock.CONNECTION[dirs[i].get3DDataValue()], PipeBlock.PipeState.CONNECTED).end();
        }
        builder.part().modelFile(pipeBaseModel(loc)).addModel().end();
    }

    private ModelFile diamondPipeConnectionModel(ResourceLocation blockLoc, String direction, ResourceLocation texture) {
        return models().withExistingParent(blockLoc.getPath() + "_connection_" + direction, modLoc("block/pipe_connection"))
                .texture("texture", texture);
    }

    private void ironItemPipeBlock(Block block) {
        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(block);
        Pipe pipe = PipesRegistry.PIPES.get(loc.getPath());
        ResourceLocation normalTex = pipe.textures().get(0);
        ResourceLocation blockedTex = pipe.textures().get(1);
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block);
        ironPipeConnection(builder, loc, normalTex, blockedTex, Direction.DOWN, 0, 0);
        ironPipeConnection(builder, loc, normalTex, blockedTex, Direction.UP, 180, 0);
        ironPipeConnection(builder, loc, normalTex, blockedTex, Direction.NORTH, 90, 180);
        ironPipeConnection(builder, loc, normalTex, blockedTex, Direction.EAST, 90, 270);
        ironPipeConnection(builder, loc, normalTex, blockedTex, Direction.SOUTH, 90, 0);
        ironPipeConnection(builder, loc, normalTex, blockedTex, Direction.WEST, 90, 90);
        builder.part().modelFile(pipeBlockedBaseModel(loc, blockedTex)).addModel().end();
    }

    private void ironPipeConnection(MultiPartBlockStateBuilder builder, ResourceLocation loc,
                                    ResourceLocation normalTex, ResourceLocation blockedTex,
                                    Direction direction, int x, int y) {
        builder.part().modelFile(pipeConnectionModelWithTex(loc, normalTex)).rotationX(x).rotationY(y).addModel()
                .condition(PipeBlock.CONNECTION[direction.get3DDataValue()], PipeBlock.PipeState.CONNECTED).end()
                .part().modelFile(pipeBlockedModel(loc, blockedTex)).rotationX(x).rotationY(y).addModel()
                .condition(PipeBlock.CONNECTION[direction.get3DDataValue()], PipeBlock.PipeState.BLOCKED).end();
    }

    private ModelFile pipeBlockedBaseModel(ResourceLocation blockLoc, ResourceLocation texture) {
        return models().withExistingParent(blockLoc.getPath() + "_base_blocked", modLoc("block/pipe_base"))
                .texture("texture", texture);
    }

    private ModelFile pipeBlockedModel(ResourceLocation blockLoc, ResourceLocation texture) {
        return models().withExistingParent(blockLoc.getPath() + "_connection_blocked", modLoc("block/pipe_connection"))
                .texture("texture", texture);
    }

    private ModelFile pipeConnectionModelWithTex(ResourceLocation blockLoc, ResourceLocation texture) {
        return models().withExistingParent(blockLoc.getPath() + "_connection", modLoc("block/pipe_connection"))
                .texture("texture", texture);
    }

    private void crateBlock(CrateBlock block) {
        horizontalBlock(block, models().cube(name(block),
                blockTexture(block, "_top"),
                blockTexture(block, "_top"),
                blockTexture(block, "_front"),
                blockTexture(block, "_side"),
                blockTexture(block, "_side"),
                blockTexture(block, "_side")
        ).texture("particle", blockTexture(block, "_top")));
    }

    private void engineBlock(Block block) {
        ResourceLocation blockLoc = key(block);
        String path = "block/engine/" + blockLoc.getPath();
        BlockModelBuilder model = models().withExistingParent(name(block), modLoc("block/engine_base"))
                .texture("top", ResourceLocation.fromNamespaceAndPath(blockLoc.getNamespace(), path + "_top"))
                .texture("side", ResourceLocation.fromNamespaceAndPath(blockLoc.getNamespace(), path + "_side"));
        facingBlock(block, model);
    }

    public void facingBlock(Block block, ModelFile model) {
        getVariantBuilder(block)
                .partialState().with(BlockStateProperties.FACING, Direction.UP)
                .modelForState().modelFile(model).addModel()
                .partialState().with(BlockStateProperties.FACING, Direction.DOWN)
                .modelForState().modelFile(model).rotationX(180).addModel()
                .partialState().with(BlockStateProperties.FACING, Direction.NORTH)
                .modelForState().modelFile(model).rotationX(90).addModel()
                .partialState().with(BlockStateProperties.FACING, Direction.SOUTH)
                .modelForState().modelFile(model).rotationX(90).rotationY(180).addModel()
                .partialState().with(BlockStateProperties.FACING, Direction.EAST)
                .modelForState().modelFile(model).rotationX(90).rotationY(90).addModel()
                .partialState().with(BlockStateProperties.FACING, Direction.WEST)
                .modelForState().modelFile(model).rotationX(90).rotationY(270).addModel();
    }

    private void tankBlock(Block block) {
        ResourceLocation blockTexture = blockTexture(block);
        ResourceLocation topTexture = suffix(blockTexture, "_top");
        ResourceLocation topJoinedTexture = suffix(blockTexture, "_top_joined");
        ResourceLocation sideTexture = suffix(blockTexture, "_side");
        ResourceLocation sideJoinedTexture = suffix(blockTexture, "_side_joined");

        getVariantBuilder(block)
                .partialState().with(TankBlock.TOP_JOINED, true).with(TankBlock.BOTTOM_JOINED, true)
                .modelForState().modelFile(tankModel(suffix(blockTexture, "_top_and_bottom_joined"), topJoinedTexture, sideJoinedTexture, topJoinedTexture)).addModel()
                .partialState().with(TankBlock.TOP_JOINED, true).with(TankBlock.BOTTOM_JOINED, false)
                .modelForState().modelFile(tankModel(suffix(blockTexture, "_top_joined"), topJoinedTexture, sideTexture, topTexture)).addModel()
                .partialState().with(TankBlock.TOP_JOINED, false).with(TankBlock.BOTTOM_JOINED, true)
                .modelForState().modelFile(tankModel(suffix(blockTexture, "_bottom_joined"), topTexture, sideJoinedTexture, topJoinedTexture)).addModel()
                .partialState().with(TankBlock.TOP_JOINED, false).with(TankBlock.BOTTOM_JOINED, false)
                .modelForState().modelFile(tankModel(blockTexture, topTexture, sideTexture, topTexture)).addModel();
    }

    private void pillarBlock(Block block) {
        pillarBlock(block, blockTexture(block));
    }

    private void pillarBlock(Block block, ResourceLocation base) {
        ResourceLocation side = suffix(base, "_side");
        ResourceLocation top = suffix(base, "_top");
        simpleBlock(
                block,
                models().cube(
                        name(block),
                        top,
                        top,
                        side,
                        side,
                        side,
                        side
                ).texture("particle", side)
        );
    }

    private void pipeBlock(Block block) {
        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(block);
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block);
        pipeConnection(builder, loc, Direction.DOWN, 0, 0);
        pipeConnection(builder, loc, Direction.UP, 180, 0);
        pipeConnection(builder, loc, Direction.NORTH, 90, 180);
        pipeConnection(builder, loc, Direction.EAST, 90, 270);
        pipeConnection(builder, loc, Direction.SOUTH, 90, 0);
        pipeConnection(builder, loc, Direction.WEST, 90, 90);
        builder.part().modelFile(pipeBaseModel(loc)).addModel().end();
    }

    private void pipeConnection(MultiPartBlockStateBuilder builder, ResourceLocation loc, Direction direction, int x, int y) {
        builder.part().modelFile(pipeConnectionModel(loc)).rotationX(x).rotationY(y).addModel()
                .condition(PipeBlock.CONNECTION[direction.get3DDataValue()], PipeBlock.PipeState.CONNECTED).end();
    }

    private void extractingPipeBlock(Block block) {
        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(block);
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block);
        extractingPipeConnection(builder, loc, Direction.DOWN, 0, 0);
        extractingPipeConnection(builder, loc, Direction.UP, 180, 0);
        extractingPipeConnection(builder, loc, Direction.NORTH, 90, 180);
        extractingPipeConnection(builder, loc, Direction.EAST, 90, 270);
        extractingPipeConnection(builder, loc, Direction.SOUTH, 90, 0);
        extractingPipeConnection(builder, loc, Direction.WEST, 90, 90);
        builder.part().modelFile(pipeBaseModel(loc)).addModel().end();
    }

    private void extractingPipeConnection(MultiPartBlockStateBuilder builder, ResourceLocation loc, Direction direction, int x, int y) {
        builder.part().modelFile(pipeConnectionModel(loc)).rotationX(x).rotationY(y).addModel()
                .condition(PipeBlock.CONNECTION[direction.get3DDataValue()], PipeBlock.PipeState.CONNECTED).end()
                .part().modelFile(pipeExtractingModel(loc)).rotationX(x).rotationY(y).addModel()
                .condition(PipeBlock.CONNECTION[direction.get3DDataValue()], PipeBlock.PipeState.EXTRACTING).end();
    }

    private ModelFile pipeBaseModel(ResourceLocation blockLoc) {
        return models().withExistingParent(blockLoc.getPath() + "_base", modLoc("block/pipe_base"))
                .texture("texture", ResourceLocation.fromNamespaceAndPath(blockLoc.getNamespace(), "block/" + blockLoc.getPath()));
    }

    private ModelFile tankModel(ResourceLocation baseLoc, ResourceLocation topLoc, ResourceLocation sideLoc, ResourceLocation bottomLoc) {
        return models().withExistingParent(baseLoc.getPath(), modLoc("block/tank_base"))
                .texture("top", ResourceLocation.fromNamespaceAndPath(topLoc.getNamespace(), topLoc.getPath()))
                .texture("bottom", ResourceLocation.fromNamespaceAndPath(bottomLoc.getNamespace(), bottomLoc.getPath()))
                .texture("side", ResourceLocation.fromNamespaceAndPath(sideLoc.getNamespace(), sideLoc.getPath()));
    }

    private ModelFile pipeConnectionModel(ResourceLocation blockLoc) {
        return models().withExistingParent(blockLoc.getPath() + "_connection", modLoc("block/pipe_connection"))
                .texture("texture", ResourceLocation.fromNamespaceAndPath(blockLoc.getNamespace(), "block/" + blockLoc.getPath()));
    }

    private ModelFile pipeExtractingModel(ResourceLocation blockLoc) {
        return models().withExistingParent(blockLoc.getPath() + "_connection_extracting", modLoc("block/pipe_connection"))
                .texture("texture", ResourceLocation.fromNamespaceAndPath(blockLoc.getNamespace(), "block/" + blockLoc.getPath() + "_extracting"));
    }

    private ResourceLocation key(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    private String name(Block block) {
        return key(block).getPath();
    }

    public ResourceLocation blockTexture(Block block, String suffix) {
        ResourceLocation name = key(block);
        return ResourceLocation.fromNamespaceAndPath(name.getNamespace(), ModelProvider.BLOCK_FOLDER + "/" + name.getPath() + suffix);
    }

    public ResourceLocation blockTexture(Block block) {
        ResourceLocation name = key(block);
        return ResourceLocation.fromNamespaceAndPath(name.getNamespace(), ModelProvider.BLOCK_FOLDER + "/" + name.getPath());
    }

    private ResourceLocation suffix(ResourceLocation rl, String suffix) {
        return rl.withSuffix(suffix);
    }

    private ResourceLocation prefix(String prefix, ResourceLocation rl) {
        return rl.withPrefix(prefix);
    }

    private ResourceLocation inDir(ResourceLocation rl, String directory) {
        StringBuilder path = new StringBuilder();
        String[] dirs = rl.getPath().split("/");
        for (int i = 0; i < dirs.length; i++) {
            if (i == dirs.length - 1) {
                path.append(directory).append("/");
            }
            path.append(dirs[i]).append(i != dirs.length - 1 ? "/" : "");
        }
        return ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), path.toString());
    }

    // ── Kinesis (Power) Pipe Blockstate Generation ───────────────────────

    private void kinesisPipeBlock(Block block) {
        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(block);
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block);
        kinesisPipeConnection(builder, loc, Direction.DOWN, 0, 0);
        kinesisPipeConnection(builder, loc, Direction.UP, 180, 0);
        kinesisPipeConnection(builder, loc, Direction.NORTH, 90, 180);
        kinesisPipeConnection(builder, loc, Direction.EAST, 90, 270);
        kinesisPipeConnection(builder, loc, Direction.SOUTH, 90, 0);
        kinesisPipeConnection(builder, loc, Direction.WEST, 90, 90);
        builder.part().modelFile(pipeBaseModel(loc)).addModel().end();
    }

    private void kinesisPipeConnection(MultiPartBlockStateBuilder builder, ResourceLocation loc, Direction direction, int x, int y) {
        builder.part().modelFile(kinesisPipeConnectionModel(loc)).rotationX(x).rotationY(y).addModel()
                .condition(PipeBlock.CONNECTION[direction.get3DDataValue()], PipeBlock.PipeState.CONNECTED).end();
    }

    private ModelFile kinesisPipeConnectionModel(ResourceLocation blockLoc) {
        return models().withExistingParent(blockLoc.getPath() + "_connection", modLoc("block/pipe_connection"))
                .texture("texture", ResourceLocation.fromNamespaceAndPath(blockLoc.getNamespace(), "block/" + blockLoc.getPath()));
    }

    private void extractingKinesisPipeBlock(Block block) {
        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(block);
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block);
        extractingKinesisPipeConnection(builder, loc, Direction.DOWN, 0, 0);
        extractingKinesisPipeConnection(builder, loc, Direction.UP, 180, 0);
        extractingKinesisPipeConnection(builder, loc, Direction.NORTH, 90, 180);
        extractingKinesisPipeConnection(builder, loc, Direction.EAST, 90, 270);
        extractingKinesisPipeConnection(builder, loc, Direction.SOUTH, 90, 0);
        extractingKinesisPipeConnection(builder, loc, Direction.WEST, 90, 90);
        builder.part().modelFile(pipeBaseModel(loc)).addModel().end();
    }

    private void extractingKinesisPipeConnection(MultiPartBlockStateBuilder builder, ResourceLocation loc, Direction direction, int x, int y) {
        builder.part().modelFile(kinesisPipeConnectionModel(loc)).rotationX(x).rotationY(y).addModel()
                .condition(PipeBlock.CONNECTION[direction.get3DDataValue()], PipeBlock.PipeState.CONNECTED).end()
                .part().modelFile(kinesisPipeExtractingModel(loc)).rotationX(x).rotationY(y).addModel()
                .condition(PipeBlock.CONNECTION[direction.get3DDataValue()], PipeBlock.PipeState.EXTRACTING).end();
    }

    private ModelFile kinesisPipeExtractingModel(ResourceLocation blockLoc) {
        return models().withExistingParent(blockLoc.getPath() + "_connection_extracting", modLoc("block/pipe_connection"))
                .texture("texture", ResourceLocation.fromNamespaceAndPath(blockLoc.getNamespace(), "block/" + blockLoc.getPath() + "_extracting"));
    }

    // Energy stripe rendering is handled by KinesisPipeBERenderer (TESR), not baked models

}
