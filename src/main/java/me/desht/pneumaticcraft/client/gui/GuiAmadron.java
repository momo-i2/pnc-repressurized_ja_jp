package me.desht.pneumaticcraft.client.gui;

import me.desht.pneumaticcraft.PneumaticCraftRepressurized;
import me.desht.pneumaticcraft.client.gui.widget.*;
import me.desht.pneumaticcraft.common.inventory.ContainerAmadron;
import me.desht.pneumaticcraft.common.inventory.ContainerAmadron.EnumProblemState;
import me.desht.pneumaticcraft.common.inventory.SlotUntouchable;
import me.desht.pneumaticcraft.common.network.NetworkHandler;
import me.desht.pneumaticcraft.common.network.PacketAmadronInvSync;
import me.desht.pneumaticcraft.common.network.PacketAmadronOrderUpdate;
import me.desht.pneumaticcraft.common.recipes.AmadronOffer;
import me.desht.pneumaticcraft.common.util.PneumaticCraftUtils;
import me.desht.pneumaticcraft.lib.Textures;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GuiAmadron extends GuiPneumaticContainerBase {
    private WidgetTextField searchBar;
    private WidgetVerticalScrollbar scrollbar;
    private int page;
    private final List<WidgetAmadronOffer> widgetOffers = new ArrayList<>();
    private boolean needsRefreshing;
    private boolean hadProblem = false;
    private GuiButtonSpecial orderButton;
    private GuiButtonSpecial addTradeButton;

    public GuiAmadron(InventoryPlayer playerInventory) {
        super(new ContainerAmadron(playerInventory.player), null, Textures.GUI_AMADRON);
        xSize = 176;
        ySize = 202;
    }

    @Override
    public void initGui() {
        super.initGui();
        String amadron = I18n.format("gui.amadron.title");
        addLabel(amadron, guiLeft + xSize / 2 - mc.fontRenderer.getStringWidth(amadron) / 2, guiTop + 5, 0xFFFFFF);
        addLabel(I18n.format("gui.search"), guiLeft + 76 - mc.fontRenderer.getStringWidth(I18n.format("gui.search")), guiTop + 41, 0xFFFFFF);

        addInfoTab(I18n.format("gui.tooltip.item.amadron_tablet"));
        addAnimatedStat("gui.tab.info.ghostSlotInteraction.title", new ItemStack(Blocks.HOPPER), 0xFF00AAFF, true).setText("gui.tab.info.ghostSlotInteraction");
        addAnimatedStat("gui.tab.amadron.disclaimer.title", new ItemStack(Items.WRITABLE_BOOK), 0xFF0000FF, true).setText("gui.tab.amadron.disclaimer");
        GuiAnimatedStat customTrades = addAnimatedStat("gui.tab.amadron.customTrades", new ItemStack(Items.DIAMOND), 0xFFD07000, false);
        List<String> text = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            text.add("                      ");
        }
        customTrades.setTextWithoutCuttingString(text);
        searchBar = new WidgetTextField(mc.fontRenderer, guiLeft + 79, guiTop + 40, 73, mc.fontRenderer.FONT_HEIGHT);
        addWidget(searchBar);
        searchBar.setFocused(true);

        scrollbar = new WidgetVerticalScrollbar(-1, guiLeft + 156, guiTop + 54, 142);
        scrollbar.setStates(1);
        scrollbar.setListening(true);
        addWidget(scrollbar);

        List<String> tooltip = PneumaticCraftUtils.convertStringIntoList(I18n.format("gui.amadron.button.order.tooltip"), 40);
        orderButton = new GuiButtonSpecial(1, guiLeft + 52, guiTop + 16, 72, 20, I18n.format("gui.amadron.button.order")).setTooltipText(tooltip);
        addWidget(orderButton);

        addTradeButton = new GuiButtonSpecial(2, 16, 16, 20, 20, "")
                .setRenderStacks(new ItemStack(Items.GOLD_INGOT));
        customTrades.addWidget(addTradeButton);
        int startX = 40;
        if (ContainerAmadron.mayAddPeriodicOffers) {
            GuiButtonSpecial addPeriodicButton = new GuiButtonSpecial(3, 40, 16, 20, 20, "")
                    .setRenderStacks(new ItemStack(Items.CLOCK)).setTooltipText(PneumaticCraftUtils.convertStringIntoList(I18n.format("gui.amadron.button.addPeriodicTrade"), 40));
            customTrades.addWidget(addPeriodicButton);
            startX += 24;
        }
        if (ContainerAmadron.mayAddStaticOffers) {
            GuiButtonSpecial addStaticButton = new GuiButtonSpecial(4, startX, 16, 20, 20, "")
                    .setRenderStacks(new ItemStack(Items.EMERALD)).setTooltipText(PneumaticCraftUtils.convertStringIntoList(I18n.format("gui.amadron.button.addStaticTrade"), 40));
            customTrades.addWidget(addStaticButton);
        }

        needsRefreshing = true;
    }

    @Override
    protected int getBackgroundTint() {
        return 0x068e2c;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ContainerAmadron container = (ContainerAmadron) inventorySlots;
        if (needsRefreshing || page != scrollbar.getState()) {
            setPage(scrollbar.getState());
        }
        for (WidgetAmadronOffer offer : widgetOffers) {
            offer.setCanBuy(container.buyableOffers[container.offers.indexOf(offer.getOffer())]);
            offer.setShoppingAmount(container.getShoppingCartAmount(offer.getOffer()));
        }
        if (!hadProblem && container.problemState != EnumProblemState.NO_PROBLEMS) {
            problemTab.openWindow();
        }
        hadProblem = container.problemState != EnumProblemState.NO_PROBLEMS;
        orderButton.enabled = !container.isBasketEmpty();
        addTradeButton.enabled = container.currentOffers < container.maxOffers;
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.amadron.button.addTrade"));
        tooltip.addAll(PneumaticCraftUtils.convertStringIntoList(I18n.format("gui.amadron.button.addTrade.tooltip"), 40));
        tooltip.add((addTradeButton.enabled ? TextFormatting.GRAY : TextFormatting.RED) + I18n.format("gui.amadron.button.addTrade.tooltip.offerCount", container.currentOffers, container.maxOffers == Integer.MAX_VALUE ? "\u221E" : container.maxOffers));
        addTradeButton.setTooltipText(tooltip);
    }

    public void setPage(int page) {
        this.page = page;
        updateVisibleOffers();
    }

    private void updateVisibleOffers() {
        needsRefreshing = false;
        final ContainerAmadron container = (ContainerAmadron) inventorySlots;
        int invSize = ContainerAmadron.ROWS * 2;
        container.clearStacks();
        List<AmadronOffer> offers = container.offers;
        List<AmadronOffer> visibleOffers = new ArrayList<AmadronOffer>();
        int skippedOffers = 0;
        int applicableOffers = 0;
        for (AmadronOffer offer : offers) {
            if (offer.passesQuery(searchBar.getText())) {
                applicableOffers++;
                if (skippedOffers < page * invSize) {
                    skippedOffers++;
                } else if (visibleOffers.size() < invSize) {
                    visibleOffers.add(offer);
                }
            }
        }

        scrollbar.setStates(Math.max(1, (applicableOffers + invSize - 1) / invSize - 1));

        widgets.removeAll(widgetOffers);
        for (int i = 0; i < visibleOffers.size(); i++) {
            AmadronOffer offer = visibleOffers.get(i);
            if (offer.getInput() instanceof ItemStack) {
                container.getSlot(i * 2).putStack((ItemStack) offer.getInput());
                ((SlotUntouchable) container.getSlot(i * 2)).setEnabled(true);
            }
            if (offer.getOutput() instanceof ItemStack) {
                container.getSlot(i * 2 + 1).putStack((ItemStack) offer.getOutput());
                ((SlotUntouchable) container.getSlot(i * 2 + 1)).setEnabled(true);
            }

            WidgetAmadronOffer widget = new WidgetAmadronOffer(i, guiLeft + 6 + 73 * (i % 2), guiTop + 55 + 35 * (i / 2), offer) {
                @Override
                public void onMouseClicked(int mouseX, int mouseY, int button) {
                    NetworkHandler.sendToServer(new PacketAmadronOrderUpdate(container.offers.indexOf(getOffer()), button, PneumaticCraftRepressurized.proxy.isSneakingInGui()));
                }
            };
            addWidget(widget);
            widgetOffers.add(widget);
        }

        // avoid drawing phantom slot highlights where there's no widget
        for (int i = visibleOffers.size() * 2; i < container.inventorySlots.size(); i++) {
            ((SlotUntouchable) container.getSlot(i)).setEnabled(false);
        }

        // the server also needs to know what's in the tablet, or the next
        // "window items" packet will empty all the client-side slots
        NetworkHandler.sendToServer(new PacketAmadronInvSync(container.getInventory()));
    }

    @Override
    public void onKeyTyped(IGuiWidget widget) {
        super.onKeyTyped(widget);
        needsRefreshing = true;
        scrollbar.setCurrentState(0);
    }

    @Override
    public void actionPerformed(IGuiWidget widget) {

        super.actionPerformed(widget);
    }

    @Override
    protected Point getInvTextOffset() {
        return null;
    }

    @Override
    protected void addProblems(List curInfo) {
        super.addProblems(curInfo);
        EnumProblemState problemState = ((ContainerAmadron) inventorySlots).problemState;
        if (problemState != EnumProblemState.NO_PROBLEMS) {
            curInfo.add(problemState.getLocalizationKey());
        }
    }
}
