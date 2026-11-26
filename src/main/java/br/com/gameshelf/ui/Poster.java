package br.com.gameshelf.ui;

import br.com.gameshelf.Game;
import javafx.geometry.*;
import javafx.scene.canvas.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import java.util.Random;

/** Original landscape illustration used when the game has no available artwork. */
public final class Poster extends Region {
    private final Canvas canvas=new Canvas();
    private final ImageView image=new ImageView();
    private final Rectangle mask=new Rectangle();
    private final int seed;
    private static final String[][] INKS={
        {"#292c59","#8078c5","#f4a388","#4c477c","#292940","#1a1c30"},
        {"#183b48","#43818a","#ffc88d","#366370","#224651","#152c37"},
        {"#402f50","#a66887","#f6bd91","#81506e","#523750","#2c2439"},
        {"#243357","#546fa3","#ebcfa7","#465b84","#293d60","#19263c"}};
    public Poster(int seed) {
        this.seed=seed; getChildren().addAll(canvas,image); setClip(mask);
        mask.setArcWidth(18);mask.setArcHeight(18);
        setMinSize(0,0); image.setMouseTransparent(true);
    }
    public Poster(Game game,Artwork.Kind kind,String local,Artwork service) {
        this(game.id().hashCode());
        service.load(game,kind,local,img->{image.setImage(img);requestLayout();});
    }
    @Override protected void layoutChildren() {
        double w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        mask.setWidth(w);mask.setHeight(h);
        Image src=image.getImage();
        if(src!=null){
            double scale=Math.max(w/src.getWidth(),h/src.getHeight());
            double sw=w/scale,sh=h/scale;
            image.setViewport(new Rectangle2D((src.getWidth()-sw)/2,(src.getHeight()-sh)/2,sw,sh));
            image.setFitWidth(w);image.setFitHeight(h);canvas.setVisible(false);
        }else if(w!=canvas.getWidth()||h!=canvas.getHeight()){
            canvas.setWidth(w);canvas.setHeight(h);draw(w,h);
        }
    }
    private void draw(double w,double h) {
        var g=canvas.getGraphicsContext2D();
        String[] p=INKS[Math.floorMod(seed,INKS.length)];
        Random r=new Random(seed);
        g.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
            new Stop(0,Color.web(p[0])),new Stop(.8,Color.web(p[1]))));g.fillRect(0,0,w,h);
        for(int i=0;i<65;i++){
            double a=r.nextDouble(.18,.8),s=r.nextDouble(.7,1.8);
            g.setFill(Color.color(.9,.92,1,a));g.fillOval(r.nextDouble()*w,r.nextDouble()*h*.66,s,s);
        }
        double radius=Math.min(w,h)*(.19+r.nextDouble()*.12),x=w*(.38+r.nextDouble()*.32),y=h*.40;
        g.setFill(Color.web(p[2]));g.fillOval(x-radius,y-radius,radius*2,radius*2);
        g.setStroke(Color.web(p[2],.50));g.setLineWidth(1);
        g.strokeOval(x-radius-5,y-radius-5,radius*2+10,radius*2+10);
        for(int layer=0;layer<3;layer++){
            double base=h*(.60+layer*.13);
            g.setFill(Color.web(p[3+layer]));g.beginPath();g.moveTo(0,h);
            int peaks=Math.max(7,(int)(w/h*14));
            for(int i=0;i<=peaks;i++)g.lineTo(w*i/peaks,base+(r.nextDouble()-.5)*h*.23);
            g.lineTo(w,h);g.closePath();g.fill();
        }
    }
}
