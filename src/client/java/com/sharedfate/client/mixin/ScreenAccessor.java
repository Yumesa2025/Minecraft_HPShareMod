package com.sharedfate.client.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenAccessor {
	@Accessor("title")
	@Mutable
	void sharedfate$setTitle(Component title);

	@Invoker("rebuildWidgets")
	void sharedfate$rebuildWidgets();

	/**
	 * 바닐라 화면에 위젯을 하나 더 얹는다.
	 *
	 * <p>{@code addRenderableWidget} 은 {@code protected} 라 밖에서 부를 수 없다. 대상 화면의
	 * 상위 클래스를 물려받는 믹스인으로 만들면 그냥 부를 수 있지만, {@code AbstractRecipeBookScreen}
	 * 에는 인수 없는 생성자가 없어 컴파일이 되지 않는다. 그래서 호출자를 내준다.
	 */
	@Invoker("addRenderableWidget")
	<T extends GuiEventListener & Renderable & NarratableEntry> T sharedfate$addRenderableWidget(
			T widget);
}
