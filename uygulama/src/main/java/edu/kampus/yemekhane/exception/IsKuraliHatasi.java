package edu.kampus.yemekhane.exception;

public class IsKuraliHatasi extends RuntimeException {
    private final int durumKodu;

    public IsKuraliHatasi(String mesaj) {
        this(mesaj, 400);
    }

    public IsKuraliHatasi(String mesaj, int durumKodu) {
        super(mesaj);
        this.durumKodu = durumKodu;
    }

    public int getDurumKodu() {
        return durumKodu;
    }
}