package net.fabricmc.example;

import net.aoqia.api.ModInitializer;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		System.out.println("Hello Leaf world!");

		System.out.println(System.getProperty("leaf.loom.test.space"));

		// Quit now, we dont need to load the whole game to know the run configs are works
		System.exit(0);
	}
}
