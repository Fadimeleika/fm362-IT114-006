package Project.Client.Views;

import java.awt.BorderLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import Project.Client.CardView;
import Project.Client.ICardControls;

public class ConnectionPanel extends JPanel {
    private String host;
    private int port;
    //UCID: fm362 Date:04/15/2024
    private JTextField usernameField; // This is JTextFeild for username input

    public ConnectionPanel(ICardControls controls) {
        super(new BorderLayout(10, 10));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
       
        //UCID: fm362 Date:04/15/2024
        // Add username info
        JLabel usernameLabel = new JLabel("Username:");
        usernameField = new JTextField();
        JLabel usernameError = new JLabel();
        content.add(usernameLabel);
        content.add(usernameField);
        content.add(usernameError);

        // add host info
        JLabel hostLabel = new JLabel("Host:");
        JTextField hostValue = new JTextField("127.0.0.1");
        JLabel hostError = new JLabel();
        content.add(hostLabel);
        content.add(hostValue);
        content.add(hostError);
        // add port info
        JLabel portLabel = new JLabel("Port:");
        JTextField portValue = new JTextField("3000");
        JLabel portError = new JLabel();
        content.add(portLabel);
        content.add(portValue);
        content.add(portError);
        // add button
        JButton button = new JButton("Connect");
        // add listener
        button.addActionListener((event) -> {
            boolean isValid = true;
            // UCID:fm362 Date:04/15/2024
            //  this is to check if username contains spaces
            if (usernameField.getText().contains(" ")) {
                usernameError.setText("Username cannot contain spaces");
                usernameError.setVisible(true);
                isValid = false;
            } else {
                usernameError.setVisible(false);
            }
            try {
                port = Integer.parseInt(portValue.getText());
                portError.setVisible(false);
                // if valid, next card

            } catch (NumberFormatException e) {
                portError.setText("Invalid port value, must be a number");
                portError.setVisible(true);
                isValid = false;
            }
            if (isValid) {
                host = hostValue.getText();
                controls.next();
            }
        });
        content.add(Box.createVerticalStrut(10));
        content.add(button);
        // filling the other slots for spacing
        this.add(new JPanel(), BorderLayout.WEST);
        this.add(new JPanel(), BorderLayout.EAST);
        this.add(new JPanel(), BorderLayout.NORTH);
        this.add(new JPanel(), BorderLayout.SOUTH);
        // add the content to the center slot
        this.add(content, BorderLayout.CENTER);
        this.setName(CardView.CONNECT.name());
        controls.addPanel(CardView.CONNECT.name(), this);
    }

    public ConnectionPanel() {
        //TODO Auto-generated constructor stub
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}

            

    
 