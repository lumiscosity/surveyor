package folk.sisby.surveyor.landmark;

import net.minecraft.component.ComponentMap;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public interface VariableLandmark<T extends VariableLandmark<T>> extends Landmark<T> {
	default @Nullable UUID owner() {
		return optionalOwner().orElse(null);
	}

	Optional<UUID> optionalOwner();

	default @Nullable DyeColor color() {
		return optionalColor().orElse(null);
	}

	Optional<DyeColor> optionalColor();

	default @Nullable Text name() {
		return optionalName().orElse(null);
	}

	Optional<Text> optionalName();

	default @Nullable ComponentMap components() {
		return optionalComponents().orElse(null);
	}

	Optional<ComponentMap> optionalComponents();

	default @Nullable Identifier texture() {
		return optionalTexture().orElse(null);
	}

	Optional<Identifier> optionalTexture();
}
