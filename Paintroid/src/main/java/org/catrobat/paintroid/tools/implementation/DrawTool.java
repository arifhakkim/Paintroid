/*
 * Paintroid: An image manipulation application for Android.
 * Copyright (C) 2010-2015 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.catrobat.paintroid.tools.implementation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.PointF;
import android.support.annotation.VisibleForTesting;

import org.catrobat.paintroid.command.Command;
import org.catrobat.paintroid.command.CommandManager;
import org.catrobat.paintroid.listener.BrushPickerView;
import org.catrobat.paintroid.tools.ToolPaint;
import org.catrobat.paintroid.tools.ToolType;
import org.catrobat.paintroid.tools.Workspace;
import org.catrobat.paintroid.ui.tools.DrawerPreview;

public class DrawTool extends BaseTool {

	@VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
	public Path pathToDraw;
	protected final PointF drawToolMovedDistance;
	protected PointF initialEventCoordinate;
	protected boolean pathInsideBitmap;
	@VisibleForTesting
	public BrushPickerView brushPickerView;

	public DrawTool(Context context, ToolPaint toolPaint, Workspace workspace, CommandManager commandManager) {
		super(context, toolPaint, workspace, commandManager);
		pathToDraw = new Path();
		pathToDraw.incReserve(1);
		drawToolMovedDistance = new PointF(0f, 0f);
		pathInsideBitmap = false;
	}

	@Override
	public void draw(Canvas canvas) {
		setPaintColor(toolPaint.getPreviewColor());

		if (getToolType() == ToolType.ERASER && toolPaint.getPreviewColor() != Color.TRANSPARENT) {
			setPaintColor(Color.TRANSPARENT);
		}

		canvas.save();
		canvas.clipRect(0, 0, workspace.getWidth(), workspace.getHeight());
		if (toolPaint.getPreviewColor() == Color.TRANSPARENT) {
			Paint previewPaint = toolPaint.getPreviewPaint();
			previewPaint.setColor(Color.BLACK);
			canvas.drawPath(pathToDraw, previewPaint);
			previewPaint.setColor(Color.TRANSPARENT);
		} else {
			canvas.drawPath(pathToDraw, toolPaint.getPaint());
		}
		canvas.restore();
	}

	@Override
	public ToolType getToolType() {
		return ToolType.BRUSH;
	}

	@Override
	public boolean handleDown(PointF coordinate) {
		if (coordinate == null) {
			return false;
		}
		initialEventCoordinate = new PointF(coordinate.x, coordinate.y);
		previousEventCoordinate = new PointF(coordinate.x, coordinate.y);
		pathToDraw.moveTo(coordinate.x, coordinate.y);
		drawToolMovedDistance.set(0, 0);
		pathInsideBitmap = false;

		pathInsideBitmap = checkPathInsideBitmap(coordinate);
		return true;
	}

	@Override
	public boolean handleMove(PointF coordinate) {
		if (initialEventCoordinate == null || previousEventCoordinate == null || coordinate == null) {
			return false;
		}
		pathToDraw.quadTo(previousEventCoordinate.x,
				previousEventCoordinate.y, coordinate.x, coordinate.y);
		pathToDraw.incReserve(1);
		drawToolMovedDistance.set(
				drawToolMovedDistance.x + Math.abs(coordinate.x - previousEventCoordinate.x),
				drawToolMovedDistance.y + Math.abs(coordinate.y - previousEventCoordinate.y));
		previousEventCoordinate.set(coordinate.x, coordinate.y);

		if (!pathInsideBitmap && checkPathInsideBitmap(coordinate)) {
			pathInsideBitmap = true;
		}
		return true;
	}

	@Override
	public boolean handleUp(PointF coordinate) {
		if (initialEventCoordinate == null || previousEventCoordinate == null || coordinate == null) {
			return false;
		}

		if (!pathInsideBitmap && checkPathInsideBitmap(coordinate)) {
			pathInsideBitmap = true;
		}

		drawToolMovedDistance.set(
				drawToolMovedDistance.x + Math.abs(coordinate.x - previousEventCoordinate.x),
				drawToolMovedDistance.y + Math.abs(coordinate.y - previousEventCoordinate.y));
		boolean returnValue;
		if (MOVE_TOLERANCE < drawToolMovedDistance.x || MOVE_TOLERANCE < drawToolMovedDistance.y) {
			returnValue = addPathCommand(coordinate);
		} else {
			returnValue = addPointCommand(initialEventCoordinate);
		}
		return returnValue;
	}

	protected boolean addPathCommand(PointF coordinate) {
		pathToDraw.lineTo(coordinate.x, coordinate.y);
		if (!pathInsideBitmap) {
			resetInternalState(StateChange.RESET_INTERNAL_STATE);
			return false;
		}
		Command command = commandFactory.createPathCommand(toolPaint.getPaint(), pathToDraw);
		commandManager.addCommand(command);
		return true;
	}

	protected boolean addPointCommand(PointF coordinate) {
		if (!pathInsideBitmap) {
			resetInternalState(StateChange.RESET_INTERNAL_STATE);
			return false;
		}
		Command command = commandFactory.createPointCommand(toolPaint.getPaint(), coordinate);
		commandManager.addCommand(command);
		return true;
	}

	@Override
	public void resetInternalState() {
		pathToDraw.rewind();
		initialEventCoordinate = null;
		previousEventCoordinate = null;
	}

	@Override
	public void changePaintColor(int color) {
		super.changePaintColor(color);
		brushPickerView.invalidate();
	}

	@Override
	public void setupToolOptions() {
		brushPickerView = new BrushPickerView(toolSpecificOptionsLayout);
		brushPickerView.setCurrentPaint(toolPaint.getPaint());
	}

	public void startTool() {
		super.startTool();
		brushPickerView.setBrushChangedListener(new BrushPickerView.OnBrushChangedListener() {
			@Override
			public void setCap(Cap strokeCap) {
				changePaintStrokeCap(strokeCap);
			}

			@Override
			public void setStrokeWidth(int strokeWidth) {
				changePaintStrokeWidth(strokeWidth);
			}
		});
		brushPickerView.setDrawerPreviewCallback(new DrawerPreview.Callback() {
			@Override
			public float getStrokeWidth() {
				return toolPaint.getStrokeWidth();
			}

			@Override
			public Cap getStrokeCap() {
				return toolPaint.getStrokeCap();
			}

			@Override
			public int getColor() {
				return toolPaint.getColor();
			}

			@Override
			public ToolType getToolType() {
				return DrawTool.this.getToolType();
			}
		});
	}

	@Override
	public void leaveTool() {
		super.leaveTool();
		brushPickerView.setBrushChangedListener(null);
		brushPickerView.setDrawerPreviewCallback(null);
	}
}
