package fr.esisar.compilation.gencode;
import fr.esisar.compilation.global.src.*;
import fr.esisar.compilation.global.src3.*;
import fr.esisar.compilation.verif.ErreurContext;

/**
 * Génération de code pour un programme JCas à partir d'un arbre décoré.
 */

class Generation {

	/**
	 * Méthode principale de génération de code. Génère du code pour l'arbre décoré
	 * a.
	 */

	static Prog coder(Arbre a) {
		Prog.ajouterGrosComment("Programme généré par JCasc");

		Generation gen = new Generation();
		Memory.init();
		Etiq etiq = Etiq.nouvelle("debordement");
		gen.coder_LISTE_DECL(a.getFils1());
		int temp = Variable.getTaille();
		Prog.ajouter(Inst.creation1(Operation.TSTO, Operande.creationOpEntier(temp)));
		Prog.ajouter(Inst.creation1(Operation.BOV, Operande.creationOpEtiq(etiq)));
		if (temp != 0) {
			Prog.ajouter(Inst.creation1(Operation.ADDSP, Operande.creationOpEntier(temp)));
		}
		gen.coder_LISTE_INST(a.getFils2());

		// Fin du programme
		// L'instruction "HALT"

		Prog.ajouter(etiq);
		Inst inst = Inst.creation0(Operation.HALT);
		// On ajoute l'instruction à la fin du programme
		Prog.ajouter(inst);

		// On retourne le programme assembleur généré
		return Prog.instance();
	}
	public void coder_LISTE_DECL(Arbre a) {
		switch (a.getNoeud()) {
		case Vide:
			return;
		case ListeDecl:
			coder_LISTE_DECL(a.getFils1());
			coder_DECL(a.getFils2());
			return;
		default:
			break;
		}
	}

	public void coder_LISTE_INST(Arbre a) {
		switch (a.getNoeud()) {
		case Vide:
			return;
		case ListeInst:
			coder_LISTE_INST(a.getFils1());
			coder_INST(a.getFils2());
			return;
		default:
			break;
		}
	}

	public void coder_DECL(Arbre a) {
		coder_LISTE_IDF(a.getFils1());
	}

	public void coder_LISTE_IDF(Arbre a) {
		switch (a.getNoeud()) {
		case Vide:
			return;
		case ListeIdent:
			coder_LISTE_IDF(a.getFils1());
			Variable.add_var(a.getFils2());// Ajoute la variable dans notre HashMap, on add le duo : Nom_var,
											// Emplacement
			return;
		default:
		}
	}

	public Operande coder_PLACE(Arbre a,Registre rd) { // Couvi, a faire en premier
		Prog.ajouterComment("Debut Place");
        if(a.getNoeud()== Noeud.Ident )
        {
            Prog.ajouter(Inst.creation2(Operation.LOAD,Operande.creationOpEntier(Variable.get_var(a.getChaine().toLowerCase())),Operande.opDirect(rd)));
        }
        else
        {
            Prog.ajouter(Inst.creation2(Operation.LOAD,Operande.creationOpEntier(Variable.get_var(a.getChaine().toLowerCase())),Operande.opDirect(rd)));
            Registre rb;
            if((rb=Memory.allocate())!= Registre.GB ) 
            {
                coder_EXP(a.getFils2(),rb);
                debord_Interval(a,rb);
                Prog.ajouter(Inst.creation2(Operation.SUB, Operande.creationOpEntier(a.getDecor().getType().getIndice().getBorneInf()), Operande.opDirect(rb)));
                Prog.ajouter(Inst.creation2(Operation.ADD,  Operande.opDirect(rb),  Operande.opDirect(rd)));
                Memory.liberate(rb);;
            }
            else
            {
                System.out.println("Erreur coder place : tout registre utilisé");
            }
            
        }
        Prog.ajouterComment("Fin Place");
    return Operande.opDirect(rd);    
    }

	public void coder_INST(Arbre a) {// Loic
		switch (a.getNoeud()) {
		case Ecriture:
			Prog.ajouterComment("Ecriture, ligne :" + a.getNumLigne());
			coder_Ecriture(a);
			Prog.ajouterComment("Fin Ecriture, ligne :" + a.getNumLigne());
			break;
		case Ligne:
			Prog.ajouterComment("NewLigne, ligne :" + a.getNumLigne());
			Prog.ajouter(Inst.creation0(Operation.WNL));
			Prog.ajouterComment("Fin NewLigne, ligne :" + a.getNumLigne());
			break;
		case Nop:
			return;
		case Affect:
			Prog.ajouterComment("Affect, ligne :" + a.getNumLigne());
			coder_Affect(a);
			Prog.ajouterComment("Fin Affect, ligne :" + a.getNumLigne());
			return;
		case Pour:
			Prog.ajouterComment("Pour, ligne :" + a.getNumLigne());
			coder_Pour(a);
			Prog.ajouterComment("Fin Pour, ligne :" + a.getNumLigne());
			return;
		case TantQue:
			Prog.ajouterComment("TantQue, ligne :" + a.getNumLigne());
			coder_TantQue(a);
			Prog.ajouterComment("Fin TantQue, ligne :" + a.getNumLigne());
			return;
		case Si:
			Prog.ajouterComment("Si, ligne :" + a.getNumLigne());
			coder_Si(a);
			Prog.ajouterComment("Fin si, ligne :" + a.getNumLigne());
			return;
		case Lecture:
			Prog.ajouterComment("Lecture, ligne :" + a.getNumLigne());
			coder_Lecture(a);
			Prog.ajouterComment("Fin Lecture, ligne :" + a.getNumLigne());
			return;
		default:
			break;
		}
	}

	// fait
	private void coder_Ecriture(Arbre a) {
		Arbre parcours = a.getFils1();
		if (parcours.getNoeud() != Noeud.Vide) {
			coder_INST(parcours.getFils1());
			NatureType exp_nat_w = parcours.getFils2().getDecor().getType().getNature();
			switch (exp_nat_w) {
			case String:
				Prog.ajouter(
						Inst.creation1(Operation.WSTR, Operande.creationOpChaine(parcours.getFils2().getChaine())));
				break;
			case Interval:
				coder_EXP(parcours.getFils2(), Registre.R1);// D'après Diapo 19 passe 3 ,
				// On met dans R1 la valeur de l'identifiant correspondant au Noeud passé en
				// paramètre, ici parcours.getFils2()
				Prog.ajouter(Inst.creation0(Operation.WINT));
				break;
			case Real:
				coder_EXP(parcours.getFils2(), Registre.R1);// D'après Diapo 19 passe 3, même chose qu'avec Interval
				Prog.ajouter(Inst.creation0(Operation.WFLOAT));
				break;
			default:
				break;
			}
		}
	}

	/* Pas sur du tout !! */
	private void coder_Lecture(Arbre a) {
		// TODO Auto-generated method stub
		/*
		 * Quand on fait un read, on stocke la valeur dans un tableau ou un ident
		 * déclaré initialement
		 */
		Registre Rd=Memory.allocate();
		coder_PLACE(a.getFils1(),Rd); // Ecrire dans index la position en memoire(pile) de ident/tableau
													// contenu dans
		// a.getFils1, cad ce qui à été déclaré avant le read

		switch (a.getFils1().getDecor().getType().getNature()) {
		case Interval:
			Prog.ajouter(Inst.creation0(Operation.RINT));
			debord_Interval(a.getFils1(), Registre.R1);// verifie qu'il n'y a pas de debordement d'intervalle
			break;
		case Real:
			Prog.ajouter(Inst.creation0(Operation.RFLOAT));
			break;

		default:
			break;
		}
		Prog.ajouter(Inst.creation2(Operation.STORE, Operande.opDirect(Registre.R1),
				Operande.creationOpIndexe(0, Registre.GB,Rd)));
		// On store la valeur contenue dans R1 qui est la valeur renvoyé par RINT dans
		// la pile à
		// l'emplacement déterminé par R0

	}

	/*
	 * Fonction de verification des bornes de l'interval
	 */
	private void debord_Interval(Arbre a, Registre rd) {
		Prog.ajouterComment("Debut Verif Debordement Interval, ligne :" + a.getNumLigne());
		Prog.ajouter(Inst.creation2(Operation.CMP, Operande.creationOpEntier(a.getDecor().getType().getBorneSup()),
				Operande.opDirect(rd)));
		Prog.ajouter(Inst.creation1(Operation.BGT, Operande.creationOpEtiq(Etiq.lEtiq("debordement.1"))));
		Prog.ajouter(Inst.creation2(Operation.CMP, Operande.creationOpEntier(a.getDecor().getType().getBorneInf()),
				Operande.opDirect(rd)));
		Prog.ajouter(Inst.creation1(Operation.BLT, Operande.creationOpEtiq(Etiq.lEtiq("debordement.1"))));
		Prog.ajouterComment("Fin Verif Debordement Interval, ligne :" + a.getNumLigne());
	}

	// fait
	private void coder_Si(Arbre a) {
		/**
		 * Le code ci dessous permet de coder un Si en assembleur qui est representé par
		 * :
		 * 
		 * CMP R0 BNE false --code du corps du si-- BRA fin faux : --code du else-- fin:
		 * --suite du code--
		 * 
		 */
		Etiq E_sinon = Etiq.nouvelle("False");
		Etiq E_fin = Etiq.nouvelle("FinSi");
		coder_CMP_BNE(a.getFils1(), 1, E_sinon); // not_equal ( on fait un saut)
		coder_LISTE_INST(a.getFils2()); // on code le "corps" du si

		Prog.ajouter(Inst.creation1(Operation.BRA, Operande.creationOpEtiq(E_fin)));// Si on a executé le corps du Si
																					// alors on doit jump a la fin et ne
																					// pas executer le potentiel else,
																					// on écrit donc un branch qui jump
																					// a l'étiquette "fin", qui
																					// representela fin du Si

		Prog.ajouter(E_sinon);// Une fois le code du corps ecrit, on ecrit notre etiquette sur laquelle on
								// jump si la condition est fausse, donc si la condition est fausse, on saute
								// bien le code du Si
		coder_LISTE_INST(a.getFils3());// On coder le corps du Else (qu'il soit vide ou non)
		Prog.ajouter(E_fin);// On ecrit la fin de notre si via l'étiquette crée plus haut
	}

	// fait
	private void coder_TantQue(Arbre a) {
		/*
		 * Coder_Inst(Noeud_Tantque(C, I)) = declare E_Cond : Etiq := Nouvelle_Etiq;
		 * E_Début : Etiq := Nouvelle_Etiq; begin Générer(BRA, E_Cond);
		 * Générer_Etiq(E_Début); Coder_Inst(I); Générer_Etiq(E_Cond); Coder_Cond(C,
		 * True, E_Début) end ;
		 */
		// TODO Auto-generated method stub
		Etiq E_cond = Etiq.nouvelle("E_cond");
		Etiq E_debut = Etiq.nouvelle("E_debut");
		Prog.ajouter(Inst.creation1(Operation.BRA, Operande.creationOpEtiq(E_cond)));
		Prog.ajouter(E_debut);
		coder_LISTE_INST(a.getFils2());
		Prog.ajouter(E_cond);
		coder_CMP_BNE(a.getFils1(), 1, E_debut);

	}

	private void coder_Pour(Arbre a) {
		/*
		 * int x = ...; for (unsigned i = 0; i < 5; i ++) { x = x + 4; }
		 * 
		 * movl $0, %ecx for: cmpl $5, %ecx jae fin_for addl $4, %eax addl $1, %ecx jmp
		 * for fin_for:
		 */
		// TODO Auto-generated method stub

		boolean Increment = (a.getFils1().getNoeud().equals(Noeud.Increment));
		Etiq boucle_for = Etiq.nouvelle("for");
		Etiq fin_boucle_for = Etiq.nouvelle("fin_for");
		int val_compteur = Variable.get_var(a.getFils1().getFils1().getChaine());// emplacement en pile de l'ident de la
																					// boucle
		int val_fin_compteur = 0;
		int temp = Variable.add_new_var();
		// Recupere valeur debut compteur : a.getFils1.getFils2
		Registre R_comp = Memory.allocate();
		coder_EXP(a.getFils1().getFils2(), R_comp); // on met la valeur du debut de compteur dans R0
		Prog.ajouter(Inst.creation2(Operation.STORE, Operande.opDirect(R_comp),
				Operande.creationOpIndirect(val_compteur, Registre.GB)));// met la valeur de val_compteur dans la pile a
																			// celle du fils2
		// Recupere valeur fin compteur : a.getFils1.getFils3
		Registre R_fin_comp = Memory.allocate();
		coder_EXP(a.getFils1().getFils3(), R_fin_comp);
		// recupere valeur val_fin_compteur
		// load val_compteur dans le registre R0
		Prog.ajouter(Inst.creation2(Operation.LOAD, Operande.creationOpIndirect(val_compteur, Registre.GB),
				Operande.opDirect(R_comp)));

		Prog.ajouter(boucle_for);

		Prog.ajouter(Inst.creation2(Operation.CMP, Operande.opDirect(R_comp), Operande.opDirect(R_fin_comp)));
		if (Increment) {
			Prog.ajouter(Inst.creation1(Operation.BGE, Operande.creationOpEtiq(fin_boucle_for)));
		} else {
			Prog.ajouter(Inst.creation1(Operation.BLE, Operande.creationOpEtiq(fin_boucle_for)));
		}

		coder_LISTE_INST(a.getFils2());

		// On incremente ou décremente la valeur du registre
		if (Increment) {
			Prog.ajouter(Inst.creation2(Operation.ADD, Operande.creationOpEntier(1), Operande.opDirect(R_comp)));
		} else {
			Prog.ajouter(Inst.creation2(Operation.SUB, Operande.creationOpEntier(1), Operande.opDirect(R_comp)));
		}
		Prog.ajouter(Inst.creation1(Operation.BRA, Operande.creationOpEtiq(boucle_for)));

		Memory.liberate(R_fin_comp);
		Memory.liberate(R_comp);
	}

	private void coder_Affect(Arbre a) {
		// TODO Auto-generated method stub
		Registre Rc=Memory.allocate();
		coder_PLACE(a.getFils1(),Rc);
		Registre Rd = Memory.allocate();
		coder_EXP(a.getFils2(), Rd);// valeur de l'affect dans R1
		NatureType affect_nat = a.getFils2().getDecor().getType().getNature();

		if (affect_nat.equals(NatureType.Array)) {
			// affectarray

		} else if (affect_nat.equals(NatureType.Interval)) {
			debord_Interval(a.getFils2(), Rd);
		}

		Prog.ajouter(Inst.creation2(Operation.STORE, Operande.opDirect(Rd),
				Operande.creationOpIndexe(0, Registre.GB, Rc)));
		Memory.liberate(Rd);
		Memory.liberate(Rc);
	}

	/**
	 * Procédure de génération de code Génère du code pour l’expression A tel que
	 * l’expression soit --évaluée dans le registre Rc. --Précondition: le registre
	 * Rc est alloué. procedure Coder_Exp (A : Arbre; Rc : Registre)
	 */
	
	/**
	 * coder_CMP_BNE : Fonction permettant de coder une condition Avec a l'arbre
	 * utilisé Val la valeur attendue de la comparaison : 1 - True, 0 - False etiq
	 * :l'etiquette sur laquelle le branchement est effectué
	 */
	private void coder_CMP_BNE(Arbre a, int val, Etiq etiq) {
		Registre Rd = Memory.allocate();
		coder_EXP(a, Rd);// dans R0, on met 1 si le boolean est vrai, 0 sinon
		Prog.ajouterComment("Registre utilisé : " + Rd.name());
		Prog.ajouter(Inst.creation2(Operation.CMP, Operande.creationOpEntier(val), Operande.opDirect(Rd)));// on test si
																											// la
																											// condition
		// est vrai ou fausse avec CMP
		Prog.ajouter(Inst.creation1(Operation.BNE, Operande.creationOpEtiq(etiq)));// Si c'est NE alors on branch à
																					// l'étiquette
		// etiq ( on fait un saut)
		Memory.liberate(Rd);
	}

	/*Retourne le type final d'un tableau dans le cas de tableaux dans un tableau*/
	private Type Array_FinalType(Type array) {
		if (array.getNature().equals(NatureType.Array)) {
			return Array_FinalType(array.getElement());
		}
		else
		{
			return array;
		}
	}
	/*Permet de calculer recursivement la taille totale d'un tableau
	 * On retourne :
	 * 1 si le type indexé n'est pas un tableau
	 * La taille du sous-tableau en memoire sinon
	 * */
	private int Array_len(Type array)
	{
		if (array.getNature().equals(NatureType.Array)) {
			int len=array.getIndice().getBorneSup()-array.getIndice().getBorneInf()+1;
			return  Array_len(array.getElement())*len;
		}
		else
		{
			return 1;
		}
		
	}
	

	/*
	 * Procédure de génération de code Génère du code pour l’expression A tel que
	 * l’expression soit --évaluée dans le registre Rc. --Précondition: le registre
	 * Rc est alloué. procedure Coder_Exp (A : Arbre; Rc : Registre)
	 */

	
	public void coder_EXP(Arbre a, Registre rc) { // Champey & Clémentin
		
		Noeud n = a.getNoeud();
		Registre rc2 = Memory.allocate();
		// Si a est une feuille de l'arbre
		if(n==Noeud.Vide || n==Noeud.Chaine || n==Noeud.Entier || n==Noeud.Reel || n==Noeud.Ident)
		{
			Prog.ajouterComment("LOAD, ligne :" + a.getNumLigne());
			coder_EXP_feuille(a, rc, Operation.LOAD);
			Prog.ajouterComment("fin LOAD, ligne :" + a.getNumLigne());
			return;
		}
		try
		{
			if(a.getFils2() != null)
				n = a.getFils2().getNoeud();
			else
				n = null;
		}
		catch(Exception e)
		{
			//nothing to do
		}
		// Si a est une opération et que le fils droit est une feuille de l'arbre
		if(n==Noeud.Vide || n==Noeud.Chaine || n==Noeud.Entier || n==Noeud.Reel || n==Noeud.Ident)
		{
			coder_EXP(a.getFils1(), rc);
			switch(a.getNoeud())
			{
			// Opérations arithmétiques à deux fils
			case Plus:
				Prog.ajouterComment("PLUS, ligne :" + a.getNumLigne());
				coder_EXP_feuille(a.getFils2(), rc, Operation.ADD);
				Prog.ajouterComment("fin PLUS, ligne :" + a.getNumLigne());
				return;
			case Moins:
				Prog.ajouterComment("MOINS, ligne :" + a.getNumLigne());
				coder_EXP_feuille(a.getFils2(), rc, Operation.SUB);
				Prog.ajouterComment("fin MOINS, ligne :" + a.getNumLigne());
				return;
			case Mult:
				Prog.ajouterComment("MULT, ligne :" + a.getNumLigne());
				coder_EXP_feuille(a.getFils2(), rc, Operation.MUL);
				Prog.ajouterComment("fin MULT, ligne :" + a.getNumLigne());
				return;
			case DivReel:
				Prog.ajouterComment("DIVREEL, ligne :" + a.getNumLigne());
				coder_EXP_feuille(a.getFils2(), rc, Operation.DIV);
				Prog.ajouterComment("fin DIVREEL, ligne :" + a.getNumLigne());
				return;
			case Reste:
				Prog.ajouterComment("RESTE, ligne :" + a.getNumLigne());
				coder_EXP_feuille(a.getFils2(), rc, Operation.MOD);
				Prog.ajouterComment("fin RESTE, ligne :" + a.getNumLigne());
				return;
			case Quotient:
				Prog.ajouterComment("QUOTIENT, ligne :" + a.getNumLigne());
				coder_EXP_feuille(a.getFils2(), rc, Operation.DIV);
				Prog.ajouterComment("fin QUOTIENT, ligne :" + a.getNumLigne());
				return;
			
			// Opérations arithmétiques à un fils
			case PlusUnaire:
				Prog.ajouterComment("PLUSUNAIRE, ligne :" + a.getNumLigne());
				Prog.ajouter(Inst.creation1(Operation.LOAD, Operande.opDirect(rc)));
				Prog.ajouterComment("fin PLUSUNAIRE, ligne :" + a.getNumLigne());
				return;
			case MoinsUnaire:
				Prog.ajouterComment("MOINSUNAIRE, ligne :" + a.getNumLigne());
				Prog.ajouter(Inst.creation2(Operation.OPP, Operande.opDirect(rc), Operande.opDirect(rc)));
				Prog.ajouterComment("fin MOINSUNAIRE, ligne :" + a.getNumLigne());
				return;
			case Conversion:
				Prog.ajouterComment("CONVERSION, ligne :" + a.getNumLigne());
			    	if(a.getFils1().getDecor().getType().getNature() != NatureType.Array)
			    	{
			      		Prog.ajouter(Inst.creation2(Operation.FLOAT, Operande.opDirect(rc), Operande.opDirect(rc)));
			    	}
			    	Prog.ajouterComment("fin CONVERSION, ligne :" + a.getNumLigne());
				return;
				
			// Opérations logiques à deux fils
			case Et:
				Prog.ajouterComment("ET, ligne :" + a.getNumLigne());
				coder_ET(a, rc);
				Prog.ajouterComment("fin ET, ligne :" + a.getNumLigne());
				return;
			case Ou:
				Prog.ajouterComment("OU, ligne :" + a.getNumLigne());
				coder_OU(a, rc);
				Prog.ajouterComment("fin OU, ligne :" + a.getNumLigne());
				return;
			case Egal:
				Prog.ajouterComment("EGAL, ligne :" + a.getNumLigne());
				coder_EXP(a.getFils2(), rc2); // On met la valeur du fils2 dans RC2
				/* CMP RC, RC2      */Prog.ajouter(Inst.creation2(Operation.CMP, Operande.opDirect(rc2), Operande.opDirect(rc)));
				/* SEQ RC           */Prog.ajouter(Inst.creation1(Operation.SEQ, Operande.opDirect(rc)));
				Prog.ajouterComment("fin EGAL, ligne :" + a.getNumLigne());
				return;
			case NonEgal:
				Prog.ajouterComment("NONEGAL, ligne :" + a.getNumLigne());
				coder_EXP(a.getFils2(), rc2); // On met la valeur du fils2 dans RC2
				/* CMP RC, RC2      */Prog.ajouter(Inst.creation2(Operation.CMP, Operande.opDirect(rc2), Operande.opDirect(rc)));
				/* SNE RC           */Prog.ajouter(Inst.creation1(Operation.SNE, Operande.opDirect(rc)));
				Prog.ajouterComment("fin NONEGAL, ligne :" + a.getNumLigne());
				return;
			case Sup:
				Prog.ajouterComment("SUP, ligne :" + a.getNumLigne());
				coder_EXP(a.getFils2(), rc2); // On met la valeur du fils2 dans RC2
				/* CMP RC, RC2      */Prog.ajouter(Inst.creation2(Operation.CMP, Operande.opDirect(rc2), Operande.opDirect(rc)));
				/* SGT RC           */Prog.ajouter(Inst.creation1(Operation.SGT, Operande.opDirect(rc)));
				Prog.ajouterComment("fin SUP, ligne :" + a.getNumLigne());
				return;
			case SupEgal:
				Prog.ajouterComment("SUPEGAL, ligne :" + a.getNumLigne());
				coder_EXP(a.getFils2(), rc2); // On met la valeur du fils2 dans RC2
				/* CMP RC, RC2      */Prog.ajouter(Inst.creation2(Operation.CMP, Operande.opDirect(rc2), Operande.opDirect(rc)));
				/* SGE RC           */Prog.ajouter(Inst.creation1(Operation.SGE, Operande.opDirect(rc)));
				Prog.ajouterComment("fin SUPEGAL, ligne :" + a.getNumLigne());
				return;
			case Inf:
				Prog.ajouterComment("INF, ligne :" + a.getNumLigne());
				coder_EXP(a.getFils2(), rc2); // On met la valeur du fils2 dans RC2
				/* CMP RC, RC2      */Prog.ajouter(Inst.creation2(Operation.CMP, Operande.opDirect(rc2), Operande.opDirect(rc)));
				/* SLT RC           */Prog.ajouter(Inst.creation1(Operation.SLT, Operande.opDirect(rc)));
				Prog.ajouterComment("fin INF, ligne :" + a.getNumLigne());
				return;
			case InfEgal:
				Prog.ajouterComment("INFEGAL, ligne :" + a.getNumLigne());
				coder_EXP(a.getFils2(), rc2); // On met la valeur du fils2 dans RC2
				/* CMP RC, RC2      */Prog.ajouter(Inst.creation2(Operation.CMP, Operande.opDirect(rc2), Operande.opDirect(rc)));
				/* SLE RC           */Prog.ajouter(Inst.creation1(Operation.SLE, Operande.opDirect(rc)));
				Prog.ajouterComment("fin INFEGAL, ligne :" + a.getNumLigne());
				return;
				
			// Opérations logiques à un fils
			case Non:
				Prog.ajouterComment("NON, ligne : " + a.getNumLigne());
				Prog.ajouter(Inst.creation1(Operation.SNE, Operande.opDirect(rc)));
				Prog.ajouterComment("fin NON, ligne :" + a.getNumLigne());
				return;
			default:
				return;
			}
		}

	}

	public void coder_EXP_feuille(Arbre a, Registre rc, Operation operation)
	{
		switch (a.getNoeud())
		{
			case Vide:
				return;
			case Chaine:
				Prog.ajouter(Inst.creation2(operation, Operande.creationOpChaine(a.getChaine()), Operande.opDirect(rc)));
				return;
			case Entier:
				Prog.ajouter(Inst.creation2(operation, Operande.creationOpEntier(a.getEntier()), Operande.opDirect(rc)));
				return;
			case Reel:
				Prog.ajouter(Inst.creation2(operation, Operande.creationOpReel(a.getReel()), Operande.opDirect(rc)));
				return;
			case Ident:
				switch(a.getChaine().toLowerCase())
				{
					case "max_int":
						Prog.ajouter(Inst.creation2(operation, Operande.creationOpEntier(java.lang.Integer.MAX_VALUE), Operande.opDirect(rc)));
						return;
					case "true":
						Prog.ajouter(Inst.creation2(operation, Operande.creationOpEntier(1), Operande.opDirect(rc)));
						return;
					case "false":
						Prog.ajouter(Inst.creation2(operation, Operande.creationOpEntier(0), Operande.opDirect(rc)));
						return;
					default:
						Prog.ajouter(Inst.creation2(Operation.LOAD,Operande.creationOpIndirect(Variable.get_var(a.getChaine().toLowerCase()), Registre.GB), Operande.opDirect(rc)));	
						return;
				}
			default:
				// cas ne pouvant pas être atteint
				return;
				
		}
	}
	
	public void coder_ET(Arbre a, Registre rc)
	{
		Etiq e1 = Etiq.nouvelle("finET");
		Etiq e2 = Etiq.nouvelle("returnFalse");
		// RC contient la valeur du fils1
		
		/* CMP RC, #0      */Prog.ajouter(Inst.creation2(Operation.CMP, Operande.creationOpEntier(0), Operande.opDirect(rc)));
		/* BEQ returnFalse */Prog.ajouter(Inst.creation1(Operation.BEQ, Operande.creationOpEtiq(e2)));
		
		coder_EXP(a.getFils2(), rc); // On met la valeur du fils2 dans RC
		
		/* CMP RC, #0      */Prog.ajouter(Inst.creation2(Operation.CMP, Operande.creationOpEntier(0), Operande.opDirect(rc)));
		/* SNE RC          */Prog.ajouter(Inst.creation1(Operation.SNE, Operande.opDirect(rc)));
		/* BRA finET       */Prog.ajouter(Inst.creation1(Operation.BRA, Operande.creationOpEtiq(e1)));
		
		/* returnFalse:    */Prog.ajouter(e2);
		/* LOAD #0, RC     */Prog.ajouter(Inst.creation2(Operation.LOAD, Operande.creationOpEntier(0), Operande.opDirect(rc)));
		
		/* finET:          */Prog.ajouter(e1);
		
		return;
	}
	
		public void coder_OU(Arbre a, Registre rc)
	{
		Etiq e1 = Etiq.nouvelle("finOU");
		Etiq e2 = Etiq.nouvelle("returnTrue");
		// RC contient la valeur du fils1
		
		/* CMP RC, #0      */Prog.ajouter(Inst.creation2(Operation.CMP, Operande.creationOpEntier(0), Operande.opDirect(rc)));
		/* BNE returnTrue */Prog.ajouter(Inst.creation1(Operation.BNE, Operande.creationOpEtiq(e2)));
		
		coder_EXP(a.getFils2(), rc); // On met la valeur du fils2 dans RC
		
		/* CMP RC, #0      */Prog.ajouter(Inst.creation2(Operation.CMP, Operande.creationOpEntier(0), Operande.opDirect(rc)));
		/* BNE returnTrue  */Prog.ajouter(Inst.creation1(Operation.BNE, Operande.creationOpEtiq(e2)));
		/* LOAD #0, RC     */Prog.ajouter(Inst.creation2(Operation.LOAD, Operande.creationOpEntier(0), Operande.opDirect(rc)));
		/* BRA finOU       */Prog.ajouter(Inst.creation1(Operation.BRA, Operande.creationOpEtiq(e1)));
		/* returnTrue:     */Prog.ajouter(e2);
		/* LOAD #1, RC     */Prog.ajouter(Inst.creation2(Operation.LOAD, Operande.creationOpEntier(1), Operande.opDirect(rc)));
		
		/*finOU:           */Prog.ajouter(e1);
		
		return;
	}
}
