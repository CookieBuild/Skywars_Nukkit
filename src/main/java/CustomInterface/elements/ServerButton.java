package main.java.CustomInterface.elements;

import cn.nukkit.form.element.ElementButton;

public class ServerButton extends ElementButton {

    public String IP;
    public Integer Port;

    public ServerButton(String text, String IP, Integer Port) {
        super(text);
        this.IP = IP;
        this.Port = Port;
    }
}
