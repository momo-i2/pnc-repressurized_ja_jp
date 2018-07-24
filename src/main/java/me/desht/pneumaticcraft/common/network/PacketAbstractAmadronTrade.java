package me.desht.pneumaticcraft.common.network;

import io.netty.buffer.ByteBuf;
import me.desht.pneumaticcraft.common.recipes.AmadronOfferCustom;

public abstract class PacketAbstractAmadronTrade<REQ extends PacketAbstractAmadronTrade<REQ>> extends AbstractPacket<REQ> {
    private AmadronOfferCustom offer;

    public PacketAbstractAmadronTrade() {
    }

    public PacketAbstractAmadronTrade(AmadronOfferCustom offer) {
        this.offer = offer;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        offer = AmadronOfferCustom.loadFromBuf(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        offer.writeToBuf(buf);
    }

    public AmadronOfferCustom getOffer() {
        return offer;
    }

}
