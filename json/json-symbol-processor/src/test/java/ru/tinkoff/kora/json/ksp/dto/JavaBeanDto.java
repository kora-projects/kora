package ru.tinkoff.kora.json.ksp.dto;

public class JavaBeanDto {


    private java.lang.String href;
    private java.lang.String text;

    /**
     * Default constructor.  Note that this does not initialize fields
     * to their default values from the schema.  If that is desired then
     * one should use <code>newBuilder()</code>.
     */
    public JavaBeanDto() {
    }

    /**
     * All-args constructor.
     *
     * @param href The new value for href
     * @param text The new value for text
     */
    public JavaBeanDto(@org.jetbrains.annotations.NotNull java.lang.String href, @org.jetbrains.annotations.Nullable java.lang.String text) {
        this.href = href;
        this.text = text;
    }

    /**
     * Gets the value of the 'href' field.
     *
     * @return The value of the 'href' field.
     */
    @org.jetbrains.annotations.NotNull
    public java.lang.String getHref() {
        return href;
    }


    /**
     * Sets the value of the 'href' field.
     *
     * @param value the value to set.
     */
    public void setHref(@org.jetbrains.annotations.NotNull java.lang.String value) {
        this.href = value;
    }

    /**
     * Gets the value of the 'text' field.
     *
     * @return The value of the 'text' field.
     */
    @org.jetbrains.annotations.Nullable
    public java.lang.String getText() {
        return text;
    }


    /**
     * Sets the value of the 'text' field.
     *
     * @param value the value to set.
     */
    public void setText(@org.jetbrains.annotations.Nullable java.lang.String value) {
        this.text = value;
    }

}
