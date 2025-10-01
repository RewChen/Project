import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

// ============ Domain Layer ============
interface Adoptable {
    double getAdoptionFee();
}

abstract class Animal implements Adoptable {
    private static long RUNNING_ID = 1000;
    private final long id = RUNNING_ID++;

    // --- Encapsulation (private field) ---
    private String name;
    private int age;
    private String sex;
    private String breed;
    private boolean vaccinated;
    private boolean adopted;

    protected Animal(String n, int a, String s, String b, boolean v) {
        setName(n);
        setAge(a);
        sex = s;
        breed = b;
        vaccinated = v;
    }

    // --- Getter / Setter ---
    public long getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getSex() { return sex; }
    public String getBreed() { return breed; }
    public boolean isVaccinated() { return vaccinated; }
    public boolean isAdopted() { return adopted; }
    public void markAdopted() { adopted = true; }

    public final void setName(String n) {
        if (n == null || n.isBlank()) throw new IllegalArgumentException("Name required");
        name = n.trim();
    }

    public final void setAge(int a) {
        if (a < 0) throw new IllegalArgumentException("Age < 0");
        age = a;
    }

    public String typeName() { return getClass().getSimpleName(); }
}

// --- Subclasses with Polymorphism ---
class Dog extends Animal {
    public Dog(String n, int a, String s, String b, boolean v) { super(n, a, s, b, v); }
    @Override public double getAdoptionFee() { return isVaccinated() ? 1800 : 2000; }
}

class Cat extends Animal {
    public Cat(String n, int a, String s, String b, boolean v) { super(n, a, s, b, v); }
    @Override public double getAdoptionFee() { return isVaccinated() ? 900 : 1200; }
}

class Rabbit extends Animal {
    public Rabbit(String n, int a, String s, String b, boolean v) { super(n, a, s, b, v); }
    @Override public double getAdoptionFee() { return 500 + getAge() * 50.0; }
}

// --- Adopter ---
class Adopter {
    private final UUID id = UUID.randomUUID();
    private String fullName;
    private String phone;

    public Adopter(String n, String p) {
        setFullName(n);
        phone = p == null ? "" : p.trim();
    }

    public void setFullName(String n) {
        if (n == null || n.isBlank()) throw new IllegalArgumentException("Full name required");
        fullName = n.trim();
    }

    @Override
    public String toString() {
        return phone.isBlank() ? fullName : fullName + " (" + phone + ")";
    }
}

// --- AdoptionRecord ---
class AdoptionRecord {
    final Animal animal;
    final Adopter adopter;
    final LocalDateTime at = LocalDateTime.now();
    final double fee;

    public AdoptionRecord(Animal a, Adopter d) {
        animal = Objects.requireNonNull(a);
        adopter = Objects.requireNonNull(d);
        fee = a.getAdoptionFee();
    }

    @Override
    public String toString() {
        return "%s adopted %s (id=%d, %s) @ %s, fee=%.2f".formatted(
                adopter,
                animal.getName(),
                animal.getId(),
                animal.getClass().getSimpleName(),
                at.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                fee
        );
    }
}

// --- Shelter ---
class Shelter {
    private final List<Animal> animals = new ArrayList<>();
    private final List<AdoptionRecord> records = new ArrayList<>();

    public void addAnimal(Animal a) { animals.add(Objects.requireNonNull(a)); }

    public List<Animal> listAvailable() {
        return animals.stream().filter(x -> !x.isAdopted()).toList();
    }

    public List<Animal> search(String type, Boolean vax, Integer min, Integer max, String sex) {
        return listAvailable().stream()
                .filter(a -> type == null || a.typeName().equalsIgnoreCase(type))
                .filter(a -> vax == null || a.isVaccinated() == vax)
                .filter(a -> min == null || a.getAge() >= min)
                .filter(a -> max == null || a.getAge() <= max)
                .filter(a -> sex == null || a.getSex().equalsIgnoreCase(sex))
                .toList();
    }

    public AdoptionRecord adopt(long id, Adopter adopter) {
        Animal a = animals.stream()
                .filter(x -> x.getId() == id)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Animal not found"));
        if (a.isAdopted()) throw new IllegalStateException("Already adopted");
        a.markAdopted();
        AdoptionRecord r = new AdoptionRecord(a, adopter);
        records.add(r);
        return r;
    }

    public List<AdoptionRecord> getRecords() { return Collections.unmodifiableList(records); }
    public double totalFees() { return records.stream().mapToDouble(x -> x.fee).sum(); }
}

// ============ GUI Layer ============
public class AdoptionGUIApp extends JFrame {
    private final Shelter shelter = new Shelter();
    private final NumberFormat moneyFmt = NumberFormat.getCurrencyInstance(new Locale("th","TH"));
    private static final Font TH_FONT = new Font("Tahoma", Font.PLAIN, 14);

    // --- Table model ---
    private final DefaultTableModel model = new DefaultTableModel(new String[]{
            "ID","ชนิด","ชื่อ","อายุ(ปี)","เพศ","สายพันธ์ุ","วัคซีน","ค่าธรรมเนียม"}, 0) {
        public boolean isCellEditable(int r,int c){ return false; }
        public Class<?> getColumnClass(int c){
            return switch(c){
                case 0 -> Long.class;
                case 3 -> Integer.class;
                case 6 -> Boolean.class;
                default -> String.class;
            };
        }
    };

    private final JTable table = new JTable(model);
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"All","Dog","Cat","Rabbit"});
    private final JComboBox<String> sexBox  = new JComboBox<>(new String[]{"All","Male","Female"});
    private final JCheckBox vaxOnly         = new JCheckBox("เฉพาะฉีดวัคซีนแล้ว");
    private final JSpinner minAge           = new JSpinner(new SpinnerNumberModel(0,0,50,1));
    private final JSpinner maxAge           = new JSpinner(new SpinnerNumberModel(50,0,50,1));
    private final JTextField adopterName    = new JTextField();
    private final JTextField adopterPhone   = new JTextField();

    public AdoptionGUIApp(){
        super("Pet Adoption (Compact)");
        initData();
        initUI();
        loadTable(shelter.listAvailable());
    }

    // --- Initial Data ---
    private void initData(){
        shelter.addAnimal(new Dog("Taro",3,"Male","Thai Ridgeback",true));
        shelter.addAnimal(new Cat("Milo",2,"Male","Mixed",false));
        shelter.addAnimal(new Rabbit("Bunny",1,"Female","Netherland Dwarf",true));
        shelter.addAnimal(new Dog("Bella",5,"Female","Labrador",true));
        shelter.addAnimal(new Cat("Luna",1,"Female","Siamese",true));
    }

    // --- UI ---
    private void initUI(){
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(980,600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8,8));

        JPanel filter = grid();
        add(filter,BorderLayout.NORTH);
        add(new JScrollPane(table),BorderLayout.CENTER);
        add(rightPanel(),BorderLayout.EAST);

        JLabel status = new JLabel("พร้อมใช้งาน");
        status.setBorder(new EmptyBorder(6,10,6,10));
        add(status,BorderLayout.SOUTH);

        JButton search = (JButton) filter.getComponent(9);
        JButton reset  = (JButton) filter.getComponent(10);

        search.addActionListener(e -> {
            var rs = filterSearch();
            loadTable(rs);
            status.setText("ผลลัพธ์: " + rs.size() + " รายการ");
        });

        reset.addActionListener(e -> {
            typeBox.setSelectedIndex(0);
            sexBox.setSelectedIndex(0);
            vaxOnly.setSelected(false);
            minAge.setValue(0);
            maxAge.setValue(50);
            loadTable(shelter.listAvailable());
            status.setText("รีเซ็ตตัวกรองแล้ว");
        });
    }

    // --- Filter panel ---
    private JPanel grid(){
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(10,10,10,10));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.gridy = 0;

        int x = 0;
        c.gridx = x++; p.add(new JLabel("ชนิด:"),c); p.add(typeBox,c);
        c.gridx = x++; p.add(new JLabel("เพศ:"),c);  c.gridx=x++; p.add(sexBox,c);
        c.gridx = x++; p.add(new JLabel("อายุระหว่าง:"),c); c.gridx=x++; p.add(minAge,c);
        c.gridx = x++; p.add(new JLabel("ถึง"),c); c.gridx=x++; p.add(maxAge,c);
        c.gridx = x++; p.add(vaxOnly,c);

        JButton btnSearch = new JButton("ค้นหา");
        JButton btnReset  = new JButton("ล้างตัวกรอง");
        c.gridx = x++; p.add(btnSearch,c);
        c.gridx = x;   p.add(btnReset,c);

        return p;
    }

    // --- Right Panel ---
    private JPanel rightPanel(){
        JPanel r = new JPanel();
        r.setLayout(new BoxLayout(r,BoxLayout.Y_AXIS));
        r.setBorder(new EmptyBorder(10,10,10,10));

        JLabel t = new JLabel("ทำรายการรับเลี้ยง");
        t.setFont(t.getFont().deriveFont(Font.BOLD,16f));
        t.setAlignmentX(Component.CENTER_ALIGNMENT);

        r.add(t);
        r.add(Box.createVerticalStrut(8));

        JLabel lblName = new JLabel("ชื่อผู้รับเลี้ยง:");
        lblName.setAlignmentX(Component.CENTER_ALIGNMENT);
        r.add(lblName);

        adopterName.setMaximumSize(new Dimension(200,30));
        adopterName.setAlignmentX(Component.CENTER_ALIGNMENT);
        r.add(adopterName);

        r.add(Box.createVerticalStrut(6));

        JLabel lblPhone = new JLabel("เบอร์โทร:");
        lblPhone.setAlignmentX(Component.CENTER_ALIGNMENT);
        r.add(lblPhone);

        adopterPhone.setMaximumSize(new Dimension(200,30));
        adopterPhone.setAlignmentX(Component.CENTER_ALIGNMENT);
        r.add(adopterPhone);

        r.add(Box.createVerticalStrut(10));

        JButton adopt = new JButton("รับเลี้ยง (ตัวที่เลือก)");
        JButton add   = new JButton("เพิ่มสัตว์ใหม่");
        JButton rec   = new JButton("ดูประวัติรับเลี้ยง");
        JButton all   = new JButton("แสดงทั้งหมด");

        for (JButton b : List.of(adopt, add, rec, all)) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(200,36));
            r.add(Box.createVerticalStrut(6));
            r.add(b);
        }

        adopt.addActionListener(this::adoptSelected);
        add.addActionListener(this::showAddAnimalDialog);
        rec.addActionListener(e -> showRecordsDialog());
        all.addActionListener(e -> loadTable(shelter.listAvailable()));

        return r;
    }

    // --- Filter search logic ---
    private List<Animal> filterSearch(){
        String type = switch (String.valueOf(typeBox.getSelectedItem())) {
            case "Dog","Cat","Rabbit" -> typeBox.getSelectedItem().toString();
            default -> null;
        };
        String sex = switch (String.valueOf(sexBox.getSelectedItem())) {
            case "Male","Female" -> sexBox.getSelectedItem().toString();
            default -> null;
        };
        Boolean vax = vaxOnly.isSelected() ? Boolean.TRUE : null;

        return shelter.search(type, vax,
                (Integer) minAge.getValue(),
                (Integer) maxAge.getValue(),
                sex);
    }

    // --- Table loader ---
    private void loadTable(List<Animal> list){
        model.setRowCount(0);
        for (Animal a : list) {
            model.addRow(new Object[]{
                    a.getId(), a.typeName(), a.getName(),
                    a.getAge(), a.getSex(), a.getBreed(),
                    a.isVaccinated(), moneyFmt.format(a.getAdoptionFee())
            });
        }
        if (model.getRowCount() > 0) table.setRowSelectionInterval(0,0);
    }

    // --- Add animal dialog ---
    private void showAddAnimalDialog(ActionEvent e){
        JPanel p = new JPanel(new GridLayout(0,2,6,6));
        JComboBox<String> type = new JComboBox<>(new String[]{"Dog","Cat","Rabbit"});
        JTextField name = new JTextField(), breed = new JTextField();
        JSpinner age = new JSpinner(new SpinnerNumberModel(1,0,50,1));
        JComboBox<String> sex = new JComboBox<>(new String[]{"Male","Female"});
        JCheckBox vax = new JCheckBox("ฉีดวัคซีนแล้ว");

        p.add(new JLabel("ชนิด:")); p.add(type);
        p.add(new JLabel("ชื่อ:")); p.add(name);
        p.add(new JLabel("อายุ(ปี):")); p.add(age);
        p.add(new JLabel("เพศ:")); p.add(sex);
        p.add(new JLabel("สายพันธ์ุ:")); p.add(breed);
        p.add(new JLabel("วัคซีน:")); p.add(vax);

        if (JOptionPane.showConfirmDialog(this, p, "เพิ่มสัตว์ใหม่",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                String t = String.valueOf(type.getSelectedItem());
                String n = name.getText().trim();
                int a = (Integer) age.getValue();
                String sx = String.valueOf(sex.getSelectedItem());
                String br = breed.getText().trim();
                boolean vx = vax.isSelected();

                Animal ani = switch (t) {
                    case "Dog" -> new Dog(n,a,sx,br,vx);
                    case "Cat" -> new Cat(n,a,sx,br,vx);
                    default -> new Rabbit(n,a,sx,br,vx);
                };

                shelter.addAnimal(ani);
                loadTable(shelter.listAvailable());
                showInfo("เพิ่มเรียบร้อย: " + ani.typeName() + " " + ani.getName(), "สำเร็จ");
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        }
    }

    // --- Adopt selected ---
    private void adoptSelected(ActionEvent e){
        int row = table.getSelectedRow();
        if (row < 0) { showError("กรุณาเลือกสัตว์จากตารางก่อน"); return; }

        long id = ((Number) model.getValueAt(row,0)).longValue();
        String n = adopterName.getText().trim();
        if (n.isBlank()) { showError("กรอกชื่อผู้รับเลี้ยง"); return; }

        try {
            var rec = shelter.adopt(id, new Adopter(n, adopterPhone.getText().trim()));
            loadTable(shelter.listAvailable());
            adopterName.setText(""); adopterPhone.setText("");

            showInfo("รับเลี้ยงสำเร็จ\n" + rec +
                    "\nรวมค่าธรรมเนียมสะสม: " + moneyFmt.format(shelter.totalFees()), "สำเร็จ");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    // --- Show records ---
    private void showRecordsDialog(){
        var rs = shelter.getRecords();
        JTextArea a = new JTextArea(15,60);
        a.setEditable(false);
        a.setFont(TH_FONT);

        if (rs.isEmpty()) {
            a.setText("ยังไม่มีประวัติการรับเลี้ยง");
        } else {
            StringBuilder sb = new StringBuilder();
            rs.forEach(r -> sb.append("- ").append(r).append('\n'));
            sb.append("\nรวมค่าธรรมเนียมทั้งหมด: ").append(moneyFmt.format(shelter.totalFees()));
            a.setText(sb.toString());
        }

        JOptionPane.showMessageDialog(this, new JScrollPane(a),
                "ประวัติการรับเลี้ยง", JOptionPane.PLAIN_MESSAGE);
    }

    // --- Custom message dialogs ---
    private void showError(String msg){
        JLabel label = new JLabel("⚠ " + msg);
        label.setFont(TH_FONT);
        JOptionPane.showMessageDialog(this, label, "แจ้งเตือน", JOptionPane.ERROR_MESSAGE);
    }

    private void showInfo(String msg, String title){
        JLabel label = new JLabel(msg);
        label.setFont(TH_FONT);
        JOptionPane.showMessageDialog(this, label, title, JOptionPane.INFORMATION_MESSAGE);
    }

    // --- Main ---
    public static void main(String[] args){
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                UIManager.put("OptionPane.messageFont", TH_FONT);
                UIManager.put("OptionPane.buttonFont", TH_FONT);
            } catch (Exception ignore) {}

            new AdoptionGUIApp().setVisible(true);
        });
    }
}
