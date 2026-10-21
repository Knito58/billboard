#!/usr/bin/env python3
"""Builds the block color table that pictures on a billboard are matched against.

A dedicated server has no textures, so the colors are measured here, from the vanilla
client jar, and shipped inside the mod. For every block whose model is a plain cube with
fully opaque sides, the four side textures are averaged to one color (first frame of an
animated texture). The server later drops blocks that are not safe as picture pixels
(falling, ticking, glowing, block entities, ...), so this list may be generous.

Usage: python3 tools/block_colors.py [minecraft version]   (default: version in gradle.properties)
Needs Pillow. Writes src/main/resources/billboard/block_colors.json.
"""
import io
import json
import os
import re
import sys
import urllib.request
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/billboard/block_colors.json')
MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
SIDES = ('north', 'south', 'east', 'west')


def minecraft_version():
	if len(sys.argv) > 1:
		return sys.argv[1]
	with open(os.path.join(ROOT, 'gradle.properties')) as f:
		return re.search(r'^minecraft_version=(.+)$', f.read(), re.M).group(1).strip()


def fetch_json(url):
	with urllib.request.urlopen(url) as r:
		return json.load(r)


def client_jar(version):
	cache = os.path.join(ROOT, 'build', f'client-{version}.jar')
	if not os.path.exists(cache):
		entry = next(v for v in fetch_json(MANIFEST)['versions'] if v['id'] == version)
		url = fetch_json(entry['url'])['downloads']['client']['url']
		os.makedirs(os.path.dirname(cache), exist_ok=True)
		print('Downloading', url)
		with urllib.request.urlopen(url) as r, open(cache + '.part', 'wb') as f:
			f.write(r.read())
		os.rename(cache + '.part', cache)
	return zipfile.ZipFile(cache)


class Assets:
	def __init__(self, jar):
		self.jar = jar
		self.names = set(jar.namelist())

	def json(self, path):
		path = 'assets/minecraft/' + path
		return json.loads(self.jar.read(path)) if path in self.names else None

	def model(self, name):
		return self.json('models/' + name.split(':')[-1] + '.json')

	def texture(self, name):
		path = 'assets/minecraft/textures/' + name.split(':')[-1] + '.png'
		if path not in self.names:
			return None
		image = Image.open(io.BytesIO(self.jar.read(path))).convert('RGBA')
		width = image.size[0]
		return image.crop((0, 0, width, width))  # first frame of an animation


def side_textures(assets, model_name):
	"""Texture names of the four sides, or None unless the model is a plain untinted cube."""
	textures = {}
	name = model_name
	while name:
		model = assets.model(name)
		if model is None:
			return None
		for key, value in model.get('textures', {}).items():
			# A texture is a name, or an object such as {"sprite": name, "force_translucent": true}.
			textures.setdefault(key, value if isinstance(value, str) else value.get('sprite'))
		if 'elements' in model:
			# Only vanilla's cube itself counts as a full cube; anything else draws its own shape.
			if name.split(':')[-1] != 'block/cube':
				return None
			if any('tintindex' in face for element in model['elements'] for face in element.get('faces', {}).values()):
				return None
		name = model.get('parent')
	result = []
	for side in SIDES:
		value = textures.get(side)
		while value and value.startswith('#'):
			value = textures.get(value[1:])
		if not value:
			return None
		result.append(value)
	return result


def default_variant(blockstate):
	"""The model of the variant a block is placed in by default (logs standing, things facing north)."""
	variants = blockstate.get('variants')
	if not variants:
		return None
	for preferred in ('', 'axis=y', 'facing=north'):
		if preferred in variants:
			chosen = variants[preferred]
			break
	else:
		chosen = next(iter(variants.values()))
	return (chosen[0] if isinstance(chosen, list) else chosen)['model']


def main():
	version = minecraft_version()
	assets = Assets(client_jar(version))
	colors = {}
	for path in sorted(assets.names):
		match = re.fullmatch(r'assets/minecraft/blockstates/(.+)\.json', path)
		if not match:
			continue
		model = default_variant(json.loads(assets.jar.read(path)))
		sides = model and side_textures(assets, model)
		if not sides:
			continue
		pixels = []
		for name in sides:
			image = assets.texture(name)
			if image is None:
				pixels = None
				break
			pixels.extend(image.get_flattened_data() if hasattr(image, 'get_flattened_data') else image.getdata())
		if not pixels or any(a < 255 for *_, a in pixels):
			continue
		n = len(pixels)
		rgb = [round(sum(p[i] for p in pixels) / n) for i in range(3)]
		colors['minecraft:' + match.group(1)] = '%02x%02x%02x' % tuple(rgb)
	os.makedirs(os.path.dirname(OUT), exist_ok=True)
	with open(OUT, 'w') as f:
		json.dump({'minecraft_version': version, 'colors': colors}, f, indent='\t')
		f.write('\n')
	print(len(colors), 'blocks written to', os.path.relpath(OUT, ROOT))


if __name__ == '__main__':
	main()
