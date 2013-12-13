package edu.mit.kerberos;

public interface KinitPrompter {
    public String[] kinitPrompter(String name, String banner,
            final Prompt[] prompts);
}
