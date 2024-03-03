package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorNetworking;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.LandmarkType;
import folk.sisby.surveyor.landmark.Landmarks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public record LandmarksAddedPacket(Map<LandmarkType<?>, Map<BlockPos, Landmark<?>>> landmarks) implements S2CPacket, C2SPacket {
    public static LandmarksAddedPacket of(Landmark<?> landmark) {
        return new LandmarksAddedPacket(Map.of(landmark.type(), Map.of(landmark.pos(), landmark)));
    }

    public static LandmarksAddedPacket read(PacketByteBuf buf) {
        return new LandmarksAddedPacket(buf.readMap(
            HashMap::new,
            b -> Landmarks.getType(b.readIdentifier()),
            b -> b.readMap(HashMap::new, PacketByteBuf::readBlockPos, b2 -> Landmarks.CODEC.decode(NbtOps.INSTANCE, b2.readNbt()).getOrThrow(false, Surveyor.LOGGER::error).getFirst().values().stream().findFirst().orElseThrow().values().stream().findFirst().orElseThrow())
        ));
    }

    @Override
    public void writeBuf(PacketByteBuf buf) {
        buf.writeMap(landmarks,
            (b, k) -> b.writeIdentifier(k.id()),
            (b, m) -> b.writeMap(m, PacketByteBuf::writeBlockPos, (b2, landmark) -> b2.writeNbt((NbtCompound) Landmarks.CODEC.encodeStart(NbtOps.INSTANCE, Map.of(landmark.type(), Map.of(landmark.pos(), landmark))).getOrThrow(false, Surveyor.LOGGER::error)))
        );
    }

    @Override
    public Identifier getId() {
        return SurveyorNetworking.LANDMARKS_ADDED;
    }
}
