package net.fabricmc.example;

import net.minecraft.block.Block;

import net.fabricmc.api.ModInitializer;
import net.aoqia.loom.LoomTestDataA;
import net.aoqia.loom.LoomTestDataB;
import net.aoqia.loom.LoomTestDataC;
import net.aoqia.loom.LoomTestDataD;
import net.aoqia.loom.LoomTestDataE;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		System.out.println("Hello Fabric world!");

		// If this doesn't compile, remapping went wrong.
		Block blockA = LoomTestDataA.referenceToMinecraft();
		Block blockB = LoomTestDataB.referenceToMinecraft();
		Block blockC = LoomTestDataC.referenceToMinecraft();
		Block blockD = LoomTestDataD.referenceToMinecraft();
		Block blockE = LoomTestDataE.referenceToMinecraft();
	}
}
