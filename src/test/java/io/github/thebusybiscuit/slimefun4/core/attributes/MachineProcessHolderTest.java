package io.github.thebusybiscuit.slimefun4.core.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineProcessor;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class MachineProcessHolderTest {

    @Test
    void defaultsToGenericMachineOperationType() {
        MachineProcessHolder<MachineOperation> holder = new GenericHolder();

        assertEquals(MachineOperation.class, holder.getMachineOperationClass());
    }

    @Test
    void canDeclareSpecificMachineOperationType() {
        MachineProcessHolder<CraftingOperation> holder = new CraftingHolder();

        assertEquals(CraftingOperation.class, holder.getMachineOperationClass());
    }

    private static final class GenericHolder implements MachineProcessHolder<MachineOperation> {

        private final MachineProcessor<MachineOperation> processor = new MachineProcessor<>(this);

        @Override
        @Nonnull
        public String getId() {
            return "GENERIC_HOLDER";
        }

        @Override
        @Nonnull
        public MachineProcessor<MachineOperation> getMachineProcessor() {
            return processor;
        }

        @Override
        @Nonnull
        public Class<? extends MachineOperation> getMachineOperationClass() {
            return MachineOperation.class;
        }
    }

    private static final class CraftingHolder implements MachineProcessHolder<CraftingOperation> {

        private final MachineProcessor<CraftingOperation> processor = new MachineProcessor<>(this);

        @Override
        @Nonnull
        public String getId() {
            return "CRAFTING_HOLDER";
        }

        @Override
        @Nonnull
        public MachineProcessor<CraftingOperation> getMachineProcessor() {
            return processor;
        }

        @Override
        @Nonnull
        public Class<? extends MachineOperation> getMachineOperationClass() {
            return CraftingOperation.class;
        }
    }
}
