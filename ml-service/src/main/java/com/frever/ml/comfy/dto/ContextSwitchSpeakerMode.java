package com.frever.ml.comfy.dto;

public enum ContextSwitchSpeakerMode {
    AfHeart(1),
    AfAlloy(2),
    AfAoede(3),
    AfBella(4),
    AfJessica(5),
    AfKore(6),
    AfNicole(7),
    AfNova(8),
    AfRiver(9),
    AfSarah(10),
    AfSky(11),
    AmAdam(12),
    AmEcho(13),
    AmEric(14),
    AmFenrir(15),
    AmLiam(16),
    AmMichael(17),
    AmOnyx(18),
    AmPuck(19),
    AmSanta(20),
    BfAlice(21),
    BfEmma(22),
    BfIsabella(23),
    BfLily(24),
    BmDaniel(25),
    BmFable(26),
    BmGeorge(27),
    BmLewis(28),
    JfAlpha(29),
    JfGongitsune(30),
    JfNezumi(31),
    JfTebukuro(32),
    JmKumo(33),
    ZfXiaobei(34),
    ZfXiaoni(35),
    ZfXiaoxiao(36),
    ZfXiaoyi(37),
    ZmYunjian(38),
    ZmYunxi(39),
    ZmYunxia(40),
    ZmYunyang(41),
    EfDora(42),
    EmAlex(43),
    EmSanta(44),
    FfSiwis(45),
    HfAlpha(46),
    HfBeta(47),
    HmOmega(48),
    HmPsi(49),
    IfSara(50),
    ImNicola(51),
    PfDora(52),
    PmAlex(53),
    PmSanta(54);
    private final int contextValue;
    public static final ContextSwitchSpeakerMode DEFAULT = BmLewis;

    public int getContextValue() {
        return contextValue;
    }

    ContextSwitchSpeakerMode(int contextValue) {
        this.contextValue = contextValue;
    }

    public static ContextSwitchSpeakerMode fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 54) {
            return DEFAULT;
        }
        for (ContextSwitchSpeakerMode value : ContextSwitchSpeakerMode.values()) {
            if (value.getContextValue() == contextValue) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid context value: " + contextValue);
    }
}
