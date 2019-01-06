/*
 * Copyright (c) 2018, Lotto <https://github.com/devLotto>
 * Copyright (c) 2018, Raqes <j.raqes@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.interfacestyles;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SpriteID;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Interface Styles",
	description = "Change the interface style to the 2005/2010 interface",
	tags = {"2005", "2010", "skin", "theme", "ui"},
	enabledByDefault = false
)
public class InterfaceStylesPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private InterfaceStylesConfig config;

	@Inject
	private SpriteManager spriteManager;

	private Map<WidgetInfo, WidgetParameters> defaultWidgetParameters = new EnumMap<>(WidgetInfo.class);

	private boolean haveFixedViewportDefaults = false;
	private boolean haveResizableViewportOldSchoolBoxDefaults = false;
	private boolean haveResizableViewportBottomLineDefaults = false;

	@Provides
	InterfaceStylesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(InterfaceStylesConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		clientThread.invoke(this::updateAllOverrides);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientThread.invoke(() ->
		{
			restoreWidgetDimensions();
			removeGameframe();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged config)
	{
		if (config.getGroup().equals("interfaceStyles"))
		{
			clientThread.invoke(this::updateAllOverrides);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		if (widgetLoaded.getGroupId() == WidgetID.FIXED_VIEWPORT_GROUP_ID ||
			widgetLoaded.getGroupId() == WidgetID.RESIZABLE_VIEWPORT_OLD_SCHOOL_BOX_GROUP_ID ||
			widgetLoaded.getGroupId() == WidgetID.RESIZABLE_VIEWPORT_BOTTOM_LINE_GROUP_ID)
		{
			updateAllOverrides();
		}
	}

	private Boolean storeWidgetDefaultDimensions(int groupId)
	{
		for (WidgetOffset widgetOffset : WidgetOffset.values())
		{
			if (widgetOffset.getWidgetInfo().getGroupId() != groupId)
			{
				continue;
			}

			Widget widget = client.getWidget(widgetOffset.getWidgetInfo());

			if (widget != null)
			{
				defaultWidgetParameters.put(widgetOffset.getWidgetInfo(), new WidgetParameters(widget));
			}
			else
			{
				return false;
			}
		}

		return true;
	}

	private void updateAllOverrides()
	{
		removeGameframe();
		overrideSprites();
		overrideWidgetSprites();

		if (!haveFixedViewportDefaults)
		{
			haveFixedViewportDefaults = storeWidgetDefaultDimensions(WidgetID.FIXED_VIEWPORT_GROUP_ID);
		}

		if (!haveResizableViewportOldSchoolBoxDefaults)
		{
			haveResizableViewportOldSchoolBoxDefaults =
				storeWidgetDefaultDimensions(WidgetID.RESIZABLE_VIEWPORT_OLD_SCHOOL_BOX_GROUP_ID);
		}

		if (!haveResizableViewportBottomLineDefaults)
		{
			haveResizableViewportBottomLineDefaults =
				storeWidgetDefaultDimensions(WidgetID.RESIZABLE_VIEWPORT_BOTTOM_LINE_GROUP_ID);
		}

		restoreWidgetDimensions();
		adjustWidgetDimensions();
	}

	private void overrideSprites()
	{
		for (SpriteOverride spriteOverride : SpriteOverride.values())
		{
			for (Skin skin : spriteOverride.getSkin())
			{
				if (skin == config.skin())
				{
					SpritePixels spritePixels = getFileSpritePixels(String.valueOf(spriteOverride.getSpriteID()), null);

					if (spriteOverride.getSpriteID() == SpriteID.COMPASS_TEXTURE)
					{
						client.setCompass(spritePixels);
					}
					else
					{
						client.getSpriteOverrides().put(spriteOverride.getSpriteID(), spritePixels);
					}
				}
			}
		}
	}

	private void restoreSprites()
	{
		client.getWidgetSpriteCache().reset();

		for (SpriteOverride spriteOverride : SpriteOverride.values())
		{
			client.getSpriteOverrides().remove(spriteOverride.getSpriteID());
		}
	}

	private void overrideWidgetSprites()
	{
		for (WidgetOverride widgetOverride : WidgetOverride.values())
		{
			if (widgetOverride.getSkin() == config.skin())
			{
				SpritePixels spritePixels = getFileSpritePixels(widgetOverride.getName(), "widget");

				if (spritePixels != null)
				{
					for (WidgetInfo widgetInfo : widgetOverride.getWidgetInfo())
					{
						client.getWidgetSpriteOverrides().put(widgetInfo.getPackedId(), spritePixels);
					}
				}
			}
		}
	}

	private void restoreWidgetSprites()
	{
		for (WidgetOverride widgetOverride : WidgetOverride.values())
		{
			for (WidgetInfo widgetInfo : widgetOverride.getWidgetInfo())
			{
				client.getWidgetSpriteOverrides().remove(widgetInfo.getPackedId());
			}
		}
	}

	private SpritePixels getFileSpritePixels(String file, String subfolder)
	{
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(config.skin().toString() + "/");

		if (subfolder != null)
		{
			stringBuilder.append(subfolder + "/");
		}

		stringBuilder.append(file + ".png");
		String filePath = stringBuilder.toString();

		try (InputStream inputStream = InterfaceStylesPlugin.class.getResourceAsStream(filePath))
		{
			log.debug("Loading: " + filePath);
			BufferedImage spriteImage = ImageIO.read(inputStream);
			return ImageUtil.getImageSpritePixels(spriteImage, client);
		}
		catch (IOException ex)
		{
			log.debug("Unable to load image: ", ex);
		}
		catch (IllegalArgumentException ex)
		{
			log.debug("Input stream of file path " + filePath + " could not be read: ", ex);
		}

		return null;
	}

	private void adjustWidgetDimensions()
	{
		for (WidgetOffset widgetOffset : WidgetOffset.values())
		{
			if (widgetOffset.getSkin() != config.skin())
			{
				continue;
			}

			Widget widget = client.getWidget(widgetOffset.getWidgetInfo());

			if (widget != null)
			{
				if (widgetOffset.getOffsetX() != null)
				{
					widget.setOriginalX(widgetOffset.getOffsetX());
					widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
				}

				if (widgetOffset.getOffsetY() != null)
				{
					widget.setOriginalY(widgetOffset.getOffsetY());
					widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
				}

				if (widgetOffset.getWidth() != null)
				{
					widget.setOriginalWidth(widgetOffset.getWidth());
					widget.setWidthMode(WidgetSizeMode.ABSOLUTE);
				}

				if (widgetOffset.getHeight() != null)
				{
					widget.setOriginalHeight(widgetOffset.getHeight());
					widget.setHeightMode(WidgetSizeMode.ABSOLUTE);
				}

				widget.revalidate();
			}
		}
	}

	private void restoreWidgetDimensions()
	{
		for (WidgetOffset widgetOffset : WidgetOffset.values())
		{
			Widget widget = client.getWidget(widgetOffset.getWidgetInfo());

			if (widget != null)
			{
				WidgetParameters widgetParameters = defaultWidgetParameters.get(widgetOffset.getWidgetInfo());

				if (widgetParameters != null)
				{
					widget.setOriginalX(widgetParameters.getOriginalX());
					widget.setOriginalY(widgetParameters.getOriginalY());
					widget.setOriginalWidth(widgetParameters.getOriginalWidth());
					widget.setOriginalHeight(widgetParameters.getOriginalHeight());
					widget.setXPositionMode(widgetParameters.getXPositionMode());
					widget.setYPositionMode(widgetParameters.getYPositionMode());
					widget.setWidthMode(widgetParameters.getWidthMode());
					widget.setHeightMode(widgetParameters.getHeightMode());
				}

				widget.revalidate();
			}
		}
	}

	private void removeGameframe()
	{
		restoreSprites();
		restoreWidgetSprites();

		BufferedImage compassImage = spriteManager.getSprite(SpriteID.COMPASS_TEXTURE, 0);

		if (compassImage != null)
		{
			SpritePixels compass = ImageUtil.getImageSpritePixels(compassImage, client);
			client.setCompass(compass);
		}
	}
}
