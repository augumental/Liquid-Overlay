package dev.liquidinput.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Vector-ish shape painter on top of GuiGraphics.
 *
 * <p>Callers work in normal GUI coordinates (floats), but everything is rasterised in real
 * framebuffer pixels, with anti-aliased rounded corners, so shapes stay crisp at any GUI scale.
 * A simple translate/scale transform is applied in software, which keeps scissor rectangles exact.</p>
 */
public final class Painter {
	public final GuiGraphics g;
	public final Font font;
	public final float gs;

	private float tx = 0, ty = 0, ts = 1;
	private final float[] stack = new float[48];
	private int sp = 0;
	private float alpha = 1f;

	private Painter(GuiGraphics g, float gs) {
		this.g = g;
		this.gs = gs;
		this.font = Minecraft.getInstance().font;
	}

	public static Painter begin(GuiGraphics g) {
		return begin(g, true);
	}

	/** @param hiRes rasterise in real framebuffer pixels (smooth) or in GUI pixels (cheaper). */
	public static Painter begin(GuiGraphics g, boolean hiRes) {
		float gs = hiRes ? (float) Minecraft.getInstance().getWindow().getGuiScale() : 1f;
		if (gs <= 0) gs = 1;
		Painter p = new Painter(g, gs);
		g.pose().pushMatrix();
		g.pose().scale(1f / gs, 1f / gs);
		return p;
	}

	public void end() {
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ transform

	public void push() {
		stack[sp++] = tx;
		stack[sp++] = ty;
		stack[sp++] = ts;
		stack[sp++] = alpha;
	}

	public void pop() {
		alpha = stack[--sp];
		ts = stack[--sp];
		ty = stack[--sp];
		tx = stack[--sp];
	}

	public void translate(float x, float y) {
		tx += x * ts;
		ty += y * ts;
	}

	public void scale(float s) {
		ts *= s;
	}

	/** Scale around a point given in current coordinates. */
	public void scaleAround(float cx, float cy, float s) {
		translate(cx, cy);
		scale(s);
		translate(-cx, -cy);
	}

	public void alpha(float a) {
		alpha *= a;
	}

	public float alpha() {
		return alpha;
	}

	private float X(float x) {
		return (tx + x * ts) * gs;
	}

	private float Y(float y) {
		return (ty + y * ts) * gs;
	}

	private float S(float v) {
		return v * ts * gs;
	}

	private int c(int color) {
		return alpha >= 1f ? color : Colors.mulAlpha(color, alpha);
	}

	/** One real pixel expressed in current units. */
	public float px() {
		return 1f / (ts * gs);
	}

	// ------------------------------------------------------------------ primitives

	public void rect(float x, float y, float w, float h, int color) {
		color = c(color);
		if (Colors.a(color) == 0) return;
		int x0 = Math.round(X(x)), y0 = Math.round(Y(y)), x1 = Math.round(X(x + w)), y1 = Math.round(Y(y + h));
		if (x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, color);
	}

	public void grad(float x, float y, float w, float h, int top, int bottom) {
		top = c(top);
		bottom = c(bottom);
		if (Colors.a(top) == 0 && Colors.a(bottom) == 0) return;
		int x0 = Math.round(X(x)), y0 = Math.round(Y(y)), x1 = Math.round(X(x + w)), y1 = Math.round(Y(y + h));
		if (x1 > x0 && y1 > y0) g.fillGradient(x0, y0, x1, y1, top, bottom);
	}

	public void hgrad(float x, float y, float w, float h, int left, int right) {
		left = c(left);
		right = c(right);
		int x0 = Math.round(X(x)), y0 = Math.round(Y(y)), x1 = Math.round(X(x + w)), y1 = Math.round(Y(y + h));
		int span = x1 - x0;
		if (span <= 0 || y1 <= y0) return;
		int step = Math.max(1, span / 64);
		for (int i = 0; i < span; i += step) {
			float t = (i + step * 0.5f) / span;
			g.fill(x0 + i, y0, Math.min(x1, x0 + i + step), y1, Colors.lerp(left, right, t));
		}
	}

	// ------------------------------------------------------------------ rounded shapes

	public void round(float x, float y, float w, float h, float r, int color) {
		round4(x, y, w, h, r, r, r, r, color, color);
	}

	public void round(float x, float y, float w, float h, float r, int top, int bottom) {
		round4(x, y, w, h, r, r, r, r, top, bottom);
	}

	public void round4(float x, float y, float w, float h, float tl, float tr, float br, float bl, int top, int bottom) {
		top = c(top);
		bottom = c(bottom);
		if (Colors.a(top) == 0 && Colors.a(bottom) == 0) return;
		float X = X(x), Y = Y(y), W = S(w), H = S(h);
		if (W <= 0 || H <= 0) return;
		float max = Math.min(W, H) / 2f;
		tl = Math.min(S(tl), max);
		tr = Math.min(S(tr), max);
		br = Math.min(S(br), max);
		bl = Math.min(S(bl), max);

		int y0 = Math.round(Y), y1 = Math.round(Y + H);
		int rows = y1 - y0;
		if (rows <= 0) return;
		int topRows = Math.min(rows, (int) Math.ceil(Math.max(tl, tr)));
		int botRows = Math.min(rows - topRows, (int) Math.ceil(Math.max(bl, br)));
		int midStart = y0 + topRows, midEnd = y1 - botRows;

		if (midEnd > midStart) {
			int ct = Colors.lerp(top, bottom, (midStart - Y) / H);
			int cb = Colors.lerp(top, bottom, (midEnd - Y) / H);
			g.fillGradient(Math.round(X), midStart, Math.round(X + W), midEnd, ct, cb);
		}
		for (int row = y0; row < y1; row++) {
			if (row >= midStart && row < midEnd) {
				row = midEnd - 1;
				continue;
			}
			float cy = row + 0.5f - Y;
			float L = X + inset(cy, H, tl, bl);
			float R = X + W - inset(cy, H, tr, br);
			span(row, L, R, Colors.lerp(top, bottom, cy / H));
		}
	}

	private static float inset(float cy, float h, float rt, float rb) {
		if (rt > 0 && cy < rt) {
			float dy = rt - cy;
			return rt - (float) Math.sqrt(Math.max(0, rt * rt - dy * dy));
		}
		if (rb > 0 && cy > h - rb) {
			float dy = cy - (h - rb);
			return rb - (float) Math.sqrt(Math.max(0, rb * rb - dy * dy));
		}
		return 0;
	}

	/** Horizontal span on one pixel row with anti-aliased ends. */
	private void span(int row, float L, float R, int color) {
		if (R - L <= 0.01f) return;
		int iL = (int) Math.ceil(L), iR = (int) Math.floor(R);
		if (iR < iL) {
			g.fill((int) Math.floor(L), row, (int) Math.floor(L) + 1, row + 1, Colors.mulAlpha(color, R - L));
			return;
		}
		if (iR > iL) g.fill(iL, row, iR, row + 1, color);
		float covL = iL - L;
		if (covL > 0.03f) g.fill(iL - 1, row, iL, row + 1, Colors.mulAlpha(color, covL));
		float covR = R - iR;
		if (covR > 0.03f) g.fill(iR, row, iR + 1, row + 1, Colors.mulAlpha(color, covR));
	}

	/** Rounded rect with a horizontal gradient (used for slider fills and accent bars). */
	public void hgradRound(float x, float y, float w, float h, float r, int left, int right) {
		left = c(left);
		right = c(right);
		float X = X(x), Y = Y(y), W = S(w), H = S(h), R = Math.min(S(r), Math.min(W, H) / 2f);
		if (W <= 0 || H <= 0) return;
		int y0 = Math.round(Y), y1 = Math.round(Y + H);
		for (int row = y0; row < y1; row++) {
			float cy = row + 0.5f - Y;
			float in = inset(cy, H, R, R);
			float L = X + in, Rr = X + W - in;
			if (Rr - L <= 0.01f) continue;
			int iL = (int) Math.ceil(L), iR = (int) Math.floor(Rr);
			int step = Math.max(1, (iR - iL) / 48);
			for (int px = iL; px < iR; px += step) {
				int e = Math.min(iR, px + step);
				g.fill(px, row, e, row + 1, Colors.lerp(left, right, ((px + e) * 0.5f - X) / W));
			}
			float covL = iL - L, covR = Rr - iR;
			if (covL > 0.03f) g.fill(iL - 1, row, iL, row + 1, Colors.mulAlpha(left, covL));
			if (covR > 0.03f) g.fill(iR, row, iR + 1, row + 1, Colors.mulAlpha(right, covR));
		}
	}

	public void hgradRound(float x, float y, float w, float h, int left, int right) {
		hgradRound(x, y, w, h, h / 2, left, right);
	}

	public void ring(float x, float y, float w, float h, float r, float thick, int color) {
		ring4(x, y, w, h, r, r, r, r, thick, color);
	}

	/** Rounded outline. thick is in GUI units (use px() for exactly one real pixel). */
	public void ring4(float x, float y, float w, float h, float tl, float tr, float br, float bl, float thick, int color) {
		color = c(color);
		if (Colors.a(color) == 0) return;
		float X = X(x), Y = Y(y), W = S(w), H = S(h), T = Math.max(1f, S(thick));
		if (W <= 0 || H <= 0) return;
		float max = Math.min(W, H) / 2f;
		tl = Math.min(S(tl), max);
		tr = Math.min(S(tr), max);
		br = Math.min(S(br), max);
		bl = Math.min(S(bl), max);
		float itl = Math.max(0, tl - T), itr = Math.max(0, tr - T), ibr = Math.max(0, br - T), ibl = Math.max(0, bl - T);
		float IX = X + T, IY = Y + T, IW = W - 2 * T, IH = H - 2 * T;

		int y0 = Math.round(Y), y1 = Math.round(Y + H);
		int topRows = (int) Math.ceil(Math.max(Math.max(tl, tr), T)) + 1;
		int botRows = (int) Math.ceil(Math.max(Math.max(bl, br), T)) + 1;
		int midStart = Math.min(y1, y0 + topRows), midEnd = Math.max(midStart, y1 - botRows);

		if (midEnd > midStart) {
			g.fill(Math.round(X), midStart, Math.round(X + T), midEnd, color);
			g.fill(Math.round(X + W - T), midStart, Math.round(X + W), midEnd, color);
		}
		for (int row = y0; row < y1; row++) {
			if (row >= midStart && row < midEnd) {
				row = midEnd - 1;
				continue;
			}
			float cy = row + 0.5f - Y;
			float L = X + inset(cy, H, tl, bl);
			float R = X + W - inset(cy, H, tr, br);
			float icy = row + 0.5f - IY;
			if (IW <= 0 || IH <= 0 || icy < 0 || icy > IH) {
				span(row, L, R, color);
			} else {
				float IL = IX + inset(icy, IH, itl, ibl);
				float IR = IX + IW - inset(icy, IH, itr, ibr);
				span(row, L, Math.max(L, IL), color);
				span(row, Math.min(R, IR), R, color);
			}
		}
	}

	/** Soft outer shadow / glow around a rounded rect. */
	public void shadow(float x, float y, float w, float h, float r, float spread, int color) {
		shadow(x, y, w, h, r, spread, color, 10);
	}

	public void shadow(float x, float y, float w, float h, float r, float spread, int color, int maxLayers) {
		color = c(color);
		int layers = Math.max(1, Math.min(maxLayers, Math.round(S(spread))));
		float step = spread / layers;
		float a0 = Colors.a(color) / 255f;
		for (int i = 1; i <= layers; i++) {
			float t = 1f - (i - 0.5f) / layers;
			float e = step * i;
			int col = Colors.withAlpha(color, Math.round(255 * a0 * t * t * 0.55f));
			// draw with alpha already applied -> temporarily bypass global alpha
			float saved = alpha;
			alpha = 1f;
			ring(x - e, y - e, w + 2 * e, h + 2 * e, r + e, step, col);
			alpha = saved;
		}
	}

	public void circle(float cx, float cy, float rad, int color) {
		color = c(color);
		if (Colors.a(color) == 0 || rad <= 0) return;
		float CX = X(cx), CY = Y(cy), R = S(rad);
		int y0 = (int) Math.floor(CY - R), y1 = (int) Math.ceil(CY + R);
		for (int row = y0; row < y1; row++) {
			float dy = row + 0.5f - CY;
			if (Math.abs(dy) > R) continue;
			float half = (float) Math.sqrt(R * R - dy * dy);
			float edge = Math.min(1f, R - Math.abs(dy) + 0.5f);
			span(row, CX - half, CX + half, edge < 1 ? Colors.mulAlpha(color, edge) : color);
		}
	}

	/** Radial soft blob (for glows / liquid highlights). Many faint coarse layers = smooth falloff, cheap. */
	public void blob(float cx, float cy, float rad, int color) {
		color = c(color);
		if (Colors.a(color) == 0 || rad <= 0) return;
		int layers = 26;
		int layerCol = Colors.withAlpha(color, Math.max(1, Math.round(Colors.a(color) * 0.055f)));
		float CX = X(cx), CY = Y(cy), R = S(rad);
		int step = Math.max(1, Math.round(R / 50f));
		for (int i = 0; i < layers; i++) {
			float r = R * (1f - i / (float) layers);
			int y0 = (int) Math.floor(CY - r), y1 = (int) Math.ceil(CY + r);
			for (int row = y0; row < y1; row += step) {
				float dy = row + step * 0.5f - CY;
				if (Math.abs(dy) > r) continue;
				float half = (float) Math.sqrt(r * r - dy * dy);
				int l = Math.round(CX - half), rr = Math.round(CX + half);
				if (rr > l) g.fill(l, row, rr, Math.min(y1, row + step), layerCol);
			}
		}
	}

	// ------------------------------------------------------------------ text

	public float textWidth(String s, float size) {
		return font.width(s) * size / 8f;
	}

	public void text(String s, float x, float y, int color, float size, boolean shadow) {
		color = c(color);
		if (Colors.a(color) < 6 || s.isEmpty()) return;
		float k = size / 8f * ts * gs;
		g.pose().pushMatrix();
		g.pose().translate(X(x), Y(y));
		g.pose().scale(k, k);
		g.drawString(font, s, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	public void text(String s, float x, float y, int color, float size) {
		text(s, x, y, color, size, false);
	}

	/** Centered horizontally on cx, vertically on cy. */
	public void textCentered(String s, float cx, float cy, int color, float size, boolean shadow) {
		float w = textWidth(s, size);
		text(s, cx - w / 2f, cy - size * 0.44f, color, size, shadow);
	}

	public void textCentered(String s, float cx, float cy, int color, float size) {
		textCentered(s, cx, cy, color, size, false);
	}

	public void textRight(String s, float rx, float y, int color, float size) {
		text(s, rx - textWidth(s, size), y, color, size, false);
	}

	/** Shrinks the text size so that it fits maxWidth. */
	public float fit(String s, float size, float maxWidth) {
		float w = textWidth(s, size);
		return w > maxWidth && w > 0 ? size * maxWidth / w : size;
	}

	// ------------------------------------------------------------------ player head

	public void face(net.minecraft.world.entity.player.PlayerSkin skin, float x, float y, float size) {
		if (skin == null) return;
		int ix = Math.round(X(x)), iy = Math.round(Y(y)), is = Math.max(1, Math.round(S(size)));
		int col = Colors.withAlpha(0xFFFFFF, Math.round(255 * Math.max(0, Math.min(1, alpha))));
		net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, skin, ix, iy, is, col);
	}

	// ------------------------------------------------------------------ layering / clipping

	public void scissor(float x, float y, float w, float h) {
		int x0 = (int) Math.floor(tx + x * ts), y0 = (int) Math.floor(ty + y * ts);
		int x1 = (int) Math.ceil(tx + (x + w) * ts), y1 = (int) Math.ceil(ty + (y + h) * ts);
		g.pose().popMatrix();
		g.enableScissor(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
		g.pose().pushMatrix();
		g.pose().scale(1f / gs, 1f / gs);
	}

	public void endScissor() {
		g.disableScissor();
	}

	/** Start a new render stratum so following shapes draw above previously drawn text. */
	public void layer() {
		g.nextStratum();
	}
}
