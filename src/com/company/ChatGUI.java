package com.company;

import javax.swing.*;

public class ChatGUI {
    private JPanel rootPanel;
    private JList<String> messagesList;
    private JButton buttonTest;
    private JTextField textField;
    private JButton buttonTest2;
    private DefaultListModel<String> listModel;
    private int lastMessageIndex;

    private ChatGUI() {
        listModel = new DefaultListModel<>();

        messagesList.setModel(listModel);

        buttonTest.addActionListener(e -> {
            sendMessage();
        });

        textField.addActionListener(e -> {
            sendMessage();
        });

    }

    private void sendMessage(){
        String message = textField.getText();
        listModel.addElement(message);
        lastMessageIndex = listModel.getSize() -1;
        textField.setText("");
        messagesList.ensureIndexIsVisible(lastMessageIndex);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("ChatGUI");
        frame.setContentPane(new ChatGUI().rootPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);
    }
}
