/*
 * MIT License
 *
 * Copyright (c) 2020 Azercoco & Technici4n
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package aztech.modern_industrialization.machines.blockentities.multiblocks;

import aztech.modern_industrialization.MIBlock;
import aztech.modern_industrialization.MIIdentifier;
import aztech.modern_industrialization.MIText;
import aztech.modern_industrialization.MITooltips;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.machines.BEP;
import aztech.modern_industrialization.machines.blockentities.hatches.LargeTankHatch;
import aztech.modern_industrialization.machines.components.ActiveShapeComponent;
import aztech.modern_industrialization.machines.components.FluidStorageComponent;
import aztech.modern_industrialization.machines.components.OrientationComponent;
import aztech.modern_industrialization.machines.gui.MachineGuiParameters;
import aztech.modern_industrialization.machines.guicomponents.FluidGUIComponent;
import aztech.modern_industrialization.machines.guicomponents.ShapeSelection;
import aztech.modern_industrialization.machines.models.MachineCasings;
import aztech.modern_industrialization.machines.models.MachineModelClientData;
import aztech.modern_industrialization.machines.multiblocks.*;
import aztech.modern_industrialization.util.Tickable;
import java.util.List;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

public class LargeTankMultiblockBlockEntity extends MultiblockMachineBlockEntity
        implements Tickable {

    private static final int[] X_SIZES = new int[] { 3, 5, 7 };
    private static final int[] Y_SIZES = new int[] { 3, 4, 5, 6, 7 };
    private static final int[] Z_SIZES = new int[] { 3, 4, 5, 6, 7 };

    public static final int BUCKET_PER_STRUCTURE_BLOCK = 64;

    private static int getXComponent(int shapeIndex) {
        return shapeIndex / 25;
    }

    private static int getYComponent(int shapeIndex) {
        return shapeIndex % 25 / 5;
    }

    private static int getZComponent(int shapeIndex) {
        return shapeIndex % 25 % 5;
    }

    private static ShapeSelection.LineInfo createLineInfo(int[] sizes, MIText baseText) {
        return new ShapeSelection.LineInfo(
                sizes.length,
                IntStream.of(sizes).mapToObj(baseText::text).toList(),
                false);
    }

    private static final ShapeTemplate[] shapeTemplates;

    static {
        shapeTemplates = new ShapeTemplate[75];
        for (int i = 0; i < shapeTemplates.length; i++) {
            shapeTemplates[i] = buildShape(i);
        }

    }

    public static void registerFluidAPI(BlockEntityType<?> bet) {
        FluidStorage.SIDED.registerForBlockEntities((be, direction) -> {
            LargeTankMultiblockBlockEntity tank = ((LargeTankMultiblockBlockEntity) be);
            if (tank.isShapeValid()) {
                return tank.fluidStorage.getFluidStorage();
            } else {
                return Storage.empty();
            }
        }, bet);
    }

    private static ShapeTemplate buildShape(int index) {
        int sizeX = X_SIZES[getXComponent(index)];
        int sizeY = Y_SIZES[getYComponent(index)];
        int sizeZ = Z_SIZES[getZComponent(index)];

        ShapeTemplate.Builder templateBuilder = new ShapeTemplate.Builder(MachineCasings.STEEL);
        SimpleMember steelCasing = SimpleMember.forBlock(MIBlock.BLOCKS.get(new MIIdentifier("steel_machine_casing")).asBlock());
        SimpleMember glass = SimpleMember.forBlock(Blocks.GLASS);
        HatchFlags hatchFlags = new HatchFlags.Builder().with(HatchType.LARGE_TANK).build();

        for (int x = -sizeX / 2; x <= sizeX / 2; x++) {
            for (int y = -1; y < sizeY - 1; y++) {
                for (int z = 0; z < sizeZ; z++) {

                    int lim = 0;

                    if (x == -sizeX / 2 || x == sizeX / 2) {
                        lim++;
                    }
                    if (y == -1 || y == sizeY - 2) {
                        lim++;
                    }
                    if (z == 0 || z == sizeZ - 1) {
                        lim++;
                    }
                    if (x != 0 || y != 0 || z != 0) {
                        if (lim == 1) {
                            templateBuilder.add(x, y, z, glass, hatchFlags);
                        } else if (lim >= 2) {
                            templateBuilder.add(x, y, z, steelCasing, hatchFlags);
                        }
                    }
                    // TODO ADD AIR EMPTY CONDITION

                }
            }
        }

        return templateBuilder.build();
    }

    @Nullable
    private ShapeMatcher shapeMatcher = null;

    private final ActiveShapeComponent activeShape;
    private final FluidStorageComponent fluidStorage;

    private FluidGUIComponent.Data oldFluidData;

    public LargeTankMultiblockBlockEntity(BEP bep) {

        super(bep, new MachineGuiParameters.Builder("large_tank", false).build(), new OrientationComponent.Params(false, false, false));

        activeShape = new ActiveShapeComponent(shapeTemplates);
        fluidStorage = new FluidStorageComponent();

        this.registerComponents(activeShape, fluidStorage);
        this.registerGuiComponent(new FluidGUIComponent.Server(this::getFluidData));

        this.registerGuiComponent(new ShapeSelection.Server(new ShapeSelection.Behavior() {
            @Override
            public void handleClick(int clickedLine, int delta) {
                int shape = activeShape.getActiveShapeIndex();
                int activeX = getXComponent(shape);
                int activeY = getYComponent(shape);
                int activeZ = getZComponent(shape);

                if (clickedLine == 0) {
                    activeX = Mth.clamp(activeX + delta, 0, X_SIZES.length - 1);
                } else if (clickedLine == 1) {
                    activeY = Mth.clamp(activeY + delta, 0, Y_SIZES.length - 1);
                } else {
                    activeZ = Mth.clamp(activeZ + delta, 0, Z_SIZES.length - 1);
                }

                int newShape = activeZ + activeY * 5 + activeX * 25;

                activeShape.setShape(LargeTankMultiblockBlockEntity.this, newShape);
            }

            @Override
            public int getCurrentIndex(int line) {
                int shape = activeShape.getActiveShapeIndex();

                return switch (line) {
                case 0 -> getXComponent(shape);
                case 1 -> getYComponent(shape);
                default -> getZComponent(shape);
                };
            }
        }, createLineInfo(X_SIZES, MIText.ShapeTextWidth), createLineInfo(Y_SIZES, MIText.ShapeTextHeight),
                createLineInfo(Z_SIZES, MIText.ShapeTextDepth)));
    }

    public FluidGUIComponent.Data getFluidData() {
        return new FluidGUIComponent.Data(fluidStorage.getFluid(), fluidStorage.getAmount(), fluidStorage.getCapacity());
    }

    public MIInventory getInventory() {
        return MIInventory.EMPTY;
    }

    @Override
    public ShapeTemplate getActiveShape() {
        return activeShape.getActiveShape();
    }

    @Override
    protected MachineModelClientData getModelData() {
        return new MachineModelClientData(null, orientation.facingDirection);

    }

    protected final void link() {
        if (shapeMatcher == null) {
            shapeMatcher = new ShapeMatcher(level, worldPosition, orientation.facingDirection, getActiveShape());
            shapeMatcher.registerListeners(level);
        }
        if (shapeMatcher.needsRematch()) {
            shapeValid.shapeValid = false;
            shapeMatcher.rematch(level);

            if (shapeMatcher.isMatchSuccessful()) {
                shapeValid.shapeValid = true;
                onMatchSuccessful();
            }

            if (shapeValid.update()) {
                sync(false);
            }
        }
    }

    @Override
    public final void unlink() {
        if (shapeMatcher != null) {
            shapeMatcher.unlinkHatches();
            shapeMatcher.unregisterListeners(level);
            shapeMatcher = null;
        }
    }

    @Override
    public void tick() {
        if (!level.isClientSide) {
            link();
            setChanged();
            if (!this.getFluidData().equals(oldFluidData)) {
                oldFluidData = this.getFluidData();
                sync(false);
            }
        }
    }

    private void onMatchSuccessful() {
        int index = activeShape.getActiveShapeIndex();
        int sizeX = X_SIZES[getXComponent(index)];
        int sizeY = Y_SIZES[getYComponent(index)];
        int sizeZ = Z_SIZES[getZComponent(index)];
        int volume = sizeX * sizeY * sizeZ;
        long capacity = (long) volume * BUCKET_PER_STRUCTURE_BLOCK * FluidConstants.BUCKET; // 64 Bucket / Block
        fluidStorage.setCapacity(capacity);

        for (var hatch : shapeMatcher.getMatchedHatches()) {
            if (hatch instanceof LargeTankHatch tankHatch) {
                tankHatch.setController(this);
            }
        }
    }

    public Storage<FluidVariant> getStorage() {
        return fluidStorage.getFluidStorage();
    }

    public FluidVariant getFluid() {
        return fluidStorage.getFluid();
    }

    public double getFullnessFraction() {
        return (double) fluidStorage.getAmount() / fluidStorage.getCapacity();
    }

    public int[] getCornerPosition() {

        int index = activeShape.getActiveShapeIndex();
        int sizeX = X_SIZES[getXComponent(index)];
        int sizeY = Y_SIZES[getYComponent(index)];
        int sizeZ = Z_SIZES[getZComponent(index)];

        BlockPos[] corners = new BlockPos[] { ShapeMatcher.toWorldPos(getBlockPos(), orientation.facingDirection, new BlockPos(-sizeX / 2 + 1, 0, 1)),
                ShapeMatcher.toWorldPos(getBlockPos(), orientation.facingDirection, new BlockPos(sizeX / 2 - 1, sizeY - 3, sizeZ - 2)), };

        int[] cornerPosition = new int[6];
        for (int i = 0; i < 3; i++) {
            cornerPosition[i] = Integer.MIN_VALUE;
            cornerPosition[i + 3] = Integer.MAX_VALUE;
        }

        for (int i = 0; i < 2; i++) {
            cornerPosition[0] = Math.max(corners[i].getX() - this.getBlockPos().getX(), cornerPosition[0]);
            cornerPosition[1] = Math.max(corners[i].getY() - this.getBlockPos().getY(), cornerPosition[1]);
            cornerPosition[2] = Math.max(corners[i].getZ() - this.getBlockPos().getZ(), cornerPosition[2]);

            cornerPosition[3] = Math.min(corners[i].getX() - this.getBlockPos().getX(), cornerPosition[3]);
            cornerPosition[4] = Math.min(corners[i].getY() - this.getBlockPos().getY(), cornerPosition[4]);
            cornerPosition[5] = Math.min(corners[i].getZ() - this.getBlockPos().getZ(), cornerPosition[5]);
        }

        return cornerPosition;

    }

    @Override
    public List<Component> getTooltips() {
        return List.of(new MITooltips.Line(MIText.LargeTankTooltips).arg(BUCKET_PER_STRUCTURE_BLOCK).build());
    }

}
