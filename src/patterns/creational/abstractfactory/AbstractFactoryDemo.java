package patterns.creational.abstractfactory;

/**
 * ABSTRACT FACTORY — create *families* of related objects without specifying concrete classes.
 *
 * Factory Method makes one product; Abstract Factory makes a whole matching set. Use it when
 * products must be used together and must stay consistent (e.g., all "Dark theme" widgets, or
 * all "Windows" vs "Mac" UI controls).
 *
 * Here: a UI toolkit that produces a matching Button + Checkbox per platform.
 */

interface Button   { void render(); }
interface Checkbox { void render(); }

class WindowsButton   implements Button   { public void render() { System.out.println("[Windows Button]"); } }
class WindowsCheckbox implements Checkbox { public void render() { System.out.println("[Windows Checkbox]"); } }
class MacButton       implements Button   { public void render() { System.out.println("(Mac Button)"); } }
class MacCheckbox     implements Checkbox { public void render() { System.out.println("(Mac Checkbox)"); } }

/** The abstract factory: creates a consistent family of widgets. */
interface GuiFactory {
    Button createButton();
    Checkbox createCheckbox();
}

class WindowsFactory implements GuiFactory {
    public Button createButton()     { return new WindowsButton(); }
    public Checkbox createCheckbox() { return new WindowsCheckbox(); }
}

class MacFactory implements GuiFactory {
    public Button createButton()     { return new MacButton(); }
    public Checkbox createCheckbox() { return new MacCheckbox(); }
}

/** Client code works only with abstract types — it can't accidentally mix Windows + Mac widgets. */
class Application {
    private final Button button;
    private final Checkbox checkbox;

    Application(GuiFactory factory) {
        this.button = factory.createButton();
        this.checkbox = factory.createCheckbox();
    }

    void renderUi() {
        button.render();
        checkbox.render();
    }
}

public class AbstractFactoryDemo {
    public static void main(String[] args) {
        String os = "mac"; // imagine this comes from the environment

        GuiFactory factory = os.equals("windows") ? new WindowsFactory() : new MacFactory();
        new Application(factory).renderUi();
    }
}
