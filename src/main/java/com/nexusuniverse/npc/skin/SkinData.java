package com.nexusuniverse.npc.skin;

/** A Mojang "textures" property: the base64 texture blob and the signature that goes with it. */
public record SkinData(String value, String signature, long fetchedAtMillis) {
}
