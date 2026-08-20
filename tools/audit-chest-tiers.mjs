#!/usr/bin/env node

import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import { mkdtemp, readFile, readdir, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import process from "node:process";
import zlib from "node:zlib";

const require = createRequire(new URL("../../e2e/package.json", import.meta.url));
const nbt = require("prismarine-nbt");

function argumentsFrom(argv) {
  const options = new Map();
  for (let index = 0; index < argv.length; index += 2) options.set(argv[index], argv[index + 1]);
  return {
    maps: path.resolve(options.get("--maps") ?? "maps"),
    config: path.resolve(options.get("--config") ?? "src/main/resources/config.yml"),
  };
}

function coordinateList(section, key) {
  const marker = `    ${key}:\n`;
  const start = section.indexOf(marker);
  if (start < 0) throw new Error(`Missing ${key}`);
  const content = section.slice(start + marker.length);
  const end = /^    [a-z][a-z-]*:/m.exec(content)?.index ?? content.length;
  return [...content.slice(0, end).matchAll(/^      - \[([^\]]+)]$/gm)]
    .map(match => match[1].split(",").map(value => Number(value.trim())));
}

function mapConfig(text, mapName) {
  const marker = `  ${mapName}:\n`;
  const start = text.indexOf(marker);
  if (start < 0) throw new Error(`Missing ${mapName}`);
  const tail = text.slice(start + marker.length);
  const end = /^  [a-z][a-z0-9-]*:$/m.exec(tail)?.index ?? tail.indexOf("\nrewards:");
  const section = tail.slice(0, end < 0 ? tail.length : end);
  const radius = Number(/^    island-chest-radius: ([0-9.]+)$/m.exec(section)?.[1]);
  if (!Number.isFinite(radius)) throw new Error(`Missing ${mapName}.island-chest-radius`);
  return { radius, spawns: coordinateList(section, "spawns"), mid: coordinateList(section, "mid-chests") };
}

function decompress(region, slot) {
  const location = region.readUInt32BE(slot * 4);
  const offset = (location >>> 8) * 4096;
  if (offset === 0) return null;
  const length = region.readUInt32BE(offset);
  const compression = region[offset + 4];
  const payload = region.subarray(offset + 5, offset + 4 + length);
  if (compression === 1) return zlib.gunzipSync(payload);
  if (compression === 2) return zlib.inflateSync(payload);
  if (compression === 3) return payload;
  throw new Error(`Unsupported region compression ${compression}`);
}

async function chestPositions(world) {
  const result = [];
  const regionDirectory = path.join(world, "region");
  for (const name of (await readdir(regionDirectory)).filter(value => value.endsWith(".mca")).sort()) {
    const region = await readFile(path.join(regionDirectory, name));
    for (let slot = 0; slot < 1024; slot += 1) {
      const payload = decompress(region, slot);
      if (!payload) continue;
      const chunk = nbt.simplify(nbt.parseUncompressed(payload, "big"));
      for (const entity of chunk.block_entities ?? chunk.TileEntities ?? []) {
        if ((entity.id ?? entity.Id) === "minecraft:chest") result.push([entity.x, entity.y, entity.z]);
      }
    }
  }
  return result.sort((left, right) => left[0] - right[0] || left[1] - right[1] || left[2] - right[2]);
}

function classify(chest, config) {
  const key = chest.join(",");
  if (config.mid.some(position => position.join(",") === key)) return "mid";
  const radiusSquared = config.radius ** 2;
  if (config.spawns.some(spawn => (spawn[0] - chest[0]) ** 2
      + (spawn[1] - chest[1]) ** 2 + (spawn[2] - chest[2]) ** 2 <= radiusSquared)) return "island";
  return "intermediate";
}

async function main() {
  const options = argumentsFrom(process.argv.slice(2));
  const configText = await readFile(options.config, "utf8");
  const temporary = await mkdtemp(path.join(os.tmpdir(), "cookiebuild-skywars-tiers-"));
  const reports = [];
  try {
    for (const archive of (await readdir(options.maps)).filter(name => /^legacy-\d+\.zip$/.test(name)).sort()) {
      const mapName = archive.slice(0, -4);
      const destination = path.join(temporary, mapName);
      execFileSync("unzip", ["-q", path.join(options.maps, archive), "-d", destination]);
      const chests = await chestPositions(destination);
      const config = mapConfig(configText, mapName);
      const missingMid = config.mid.filter(mid => !chests.some(chest => chest.join(",") === mid.join(",")));
      if (missingMid.length > 0) throw new Error(`${mapName} has configured middle chests absent from its archive`);
      const tiers = { island: 0, intermediate: 0, mid: 0 };
      chests.forEach(chest => { tiers[classify(chest, config)] += 1; });
      if (tiers.mid !== 8) throw new Error(`${mapName} must expose exactly 8 middle chests`);
      reports.push({ map: mapName, total: chests.length, tiers });
    }
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
  process.stdout.write(`${JSON.stringify(reports, null, 2)}\n`);
}

await main();
